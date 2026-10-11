package com.back.auth.social.controller;

import com.back.auth.social.dto.GooglePendingLinkResponse;
import com.back.auth.social.exception.GoogleAccountNotLinkedException;
import com.back.auth.social.service.SocialAccountService;
import com.back.global.oauth.GoogleIntegrationException;
import com.back.global.oauth.GoogleProperties;
import com.back.global.oauth.KakaoOAuthClient;
import com.back.global.oauth.OAuthProvider;
import com.back.global.oauth.OAuthStateCookie;
import com.back.global.oauth.OAuthStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * 네이버·카카오 소셜 로그인 — 구글(GoogleAuthController)과 같은 흐름. {provider} 는 naver | kakao.
 *
 * 돌아가는 주소: 로그인 성공 `/login?login=naver` · 미연결 `/login?socialLink=naver&matched=0|1&hint=…`
 * · 실패 `/login?error=NAVER_CANCELLED|INVALID_STATE|NAVER_NOT_LINKED|NAVER_LOGIN_FAILED`
 * · 계정 연결 `/mypage?naver=linked|already_linked|failed` (카카오는 kakao).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "로그인-네이버·카카오", description = "네이버·카카오 소셜 로그인 (연결된 계정으로만 로그인 가능 — 소셜 계정만으로는 가입되지 않는다)")
public class SocialAuthController {

    private final GoogleProperties properties;
    private final SocialAccountService accountService;
    private final OAuthStateService stateService;
    private final OAuthStateCookie stateCookie;
    private final KakaoOAuthClient kakaoClient;

    @Operation(summary = "소셜 로그인 - 동의 화면 URL 발급", description = "응답 {authorizeUrl}. 키가 아직 없으면 503 {message}. state 는 5분, 같은 값을 HttpOnly 쿠키로 심는다")
    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Map<String, String>> authorize(@PathVariable String provider, HttpServletResponse response) {
        String p = requireProvider(provider);
        SocialAccountService.Authorize authorize = accountService.buildLoginAuthorize(p);
        stateCookie.bindState(response, authorize.state());
        return ResponseEntity.ok(Map.of("authorizeUrl", authorize.url()));
    }

    @Operation(summary = "소셜 로그인 - 콜백 (제공자가 호출)", description = "프론트가 부르는 API 가 아니다. 처리 뒤 프론트로 302")
    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        String p = OAuthProvider.fromPath(provider);
        if (p == null) return redirect("/login", "error", "INVALID_STATE");
        String lower = p.toLowerCase(Locale.ROOT);
        if (error != null) {
            log.info("{} 동의 취소 또는 오류: {}", p, error);
            return redirect("/login", "error", p + "_CANCELLED");
        }
        if (!stateCookie.consumeState(request, response, state)) {
            log.info("{} 콜백 - state 쿠키 불일치", p);
            return redirect("/login", "error", "INVALID_STATE");
        }
        OAuthStateService.StateData data;
        try {
            data = stateService.consume(state);
        } catch (Exception e) {
            log.info("{} 콜백 state 검증 실패: {}", p, e.getMessage());
            return redirect("/login", "error", "INVALID_STATE");
        }
        boolean isLink = OAuthStateService.PURPOSE_LINK.equals(data.purpose());
        try {
            if (isLink) {
                if (data.userId() == null) return redirect("/mypage", lower, "failed");
                accountService.linkAccount(p, data.userId(), code, state);
                return redirect("/mypage", lower, "linked");
            }
            accountService.login(p, code, state, response);
            return redirect("/login", "login", lower);
        } catch (GoogleAccountNotLinkedException e) {
            stateCookie.bindPendingLink(response, e.getPendingToken());
            return redirect("/login", "socialLink", lower, "matched", e.isEmailMatched() ? "1" : "0", "hint", e.getMaskedEmail());
        } catch (GoogleIntegrationException e) {
            log.info("{} 콜백 실패 (purpose={}): {}", p, data.purpose(), e.getMessage());
            if (isLink) return redirect("/mypage", lower, e.getStatus() == HttpStatus.CONFLICT ? "already_linked" : "failed");
            return redirect("/login", "error", e.getStatus() == HttpStatus.CONFLICT ? p + "_NOT_LINKED" : p + "_LOGIN_FAILED");
        } catch (Exception e) {
            log.error("{} 콜백 처리 중 예기치 못한 오류 (purpose={})", p, data.purpose(), e);
            return isLink ? redirect("/mypage", lower, "failed") : redirect("/login", "error", p + "_LOGIN_FAILED");
        }
    }

    @Operation(summary = "소셜 로그인 - 연결 대기 상태 조회", description = "구글과 같은 보관함(쿠키 g_pending_link)을 읽는다. 응답에 provider(GOOGLE|NAVER|KAKAO)가 실린다")
    @GetMapping("/{provider}/pending")
    public ResponseEntity<GooglePendingLinkResponse> pending(@PathVariable String provider, HttpServletRequest request) {
        requireProvider(provider);
        GooglePendingLinkResponse info = accountService.getPendingLink(stateCookie.readPendingLink(request));
        return ResponseEntity.ok(info == null ? GooglePendingLinkResponse.none() : info);
    }

    @Operation(summary = "소셜 로그인 - 연결 대기 취소", description = "204")
    @DeleteMapping("/{provider}/pending")
    public ResponseEntity<Void> discardPending(@PathVariable String provider, HttpServletRequest request, HttpServletResponse response) {
        requireProvider(provider);
        accountService.discardPendingLink(stateCookie.readPendingLink(request));
        stateCookie.clearPendingLink(response);
        return ResponseEntity.noContent().build();
    }

    /**
     * 카카오 '계정 상태 변경 웹훅' — 사용자가 카카오 쪽에서 연결을 끊거나(user.unlinked) 토큰을 모두 거둬 가면(tokens.revoked)
     * 우리 쪽 연결도 지운다. 발신자 확인은 Authorization: KakaoAK {admin key}. admin-key 가 properties 에 없으면 받지 않는다(503).
     */
    @Operation(summary = "카카오 계정 상태 변경 웹훅 (카카오가 호출)", description = "body { app_id, user_id, event, ... }. user.unlinked · tokens.revoked 면 연결 해제")
    @PostMapping("/kakao/webhook")
    public ResponseEntity<Map<String, String>> kakaoWebhook(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                            @RequestBody(required = false) Map<String, Object> body) {
        String adminKey = kakaoClient.adminKey();
        if (adminKey == null || adminKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "웹훅 키가 설정되지 않았습니다."));
        }
        if (authorization == null || !authorization.equals("KakaoAK " + adminKey)) {
            log.warn("카카오 웹훅 - 발신자 확인 실패");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "unauthorized"));
        }
        String event = body == null ? null : String.valueOf(body.get("event"));
        Object userId = body == null ? null : body.get("user_id");
        log.info("카카오 웹훅 - event={}, user_id={}", event, userId);
        if (userId != null && ("user.unlinked".equals(event) || "tokens.revoked".equals(event))) {
            accountService.unlinkByProviderUser(OAuthProvider.KAKAO, String.valueOf(userId));
        }
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    /**
     * 네이버 '연결 끊기 콜백' — 사용자가 네이버 > 연결된 서비스 관리에서 끊거나 네이버를 탈퇴하면 네이버가 이 주소를 부른다.
     * 전달 형식은 네이버 쪽 문서를 따르며, 사용자 식별값이 오면 우리 쪽 연결도 지운다. 어떤 값이 오는지 로그로 남겨 맞춘다.
     */
    @Operation(summary = "네이버 연결 끊기 콜백 (네이버가 호출)", description = "GET/POST 모두 받는다. 200")
    @RequestMapping(value = "/naver/unlink", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    public ResponseEntity<Map<String, String>> naverUnlink(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        log.info("네이버 연결 끊기 콜백 - params={}", params.keySet());
        for (String key : new String[]{"id", "user_id", "userId", "uniqueId", "naver_id"}) {
            String[] v = params.get(key);
            if (v != null && v.length > 0 && !v[0].isBlank()) {
                accountService.unlinkByProviderUser(OAuthProvider.NAVER, v[0]);
                break;
            }
        }
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    private String requireProvider(String provider) {
        String p = OAuthProvider.fromPath(provider);
        if (p == null) throw new GoogleIntegrationException("지원하지 않는 로그인 방식입니다.", HttpStatus.NOT_FOUND);
        return p;
    }

    private ResponseEntity<Void> redirect(String path, String... keyValues) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getFrontendUrl()).path(path);
        for (int i = 0; i + 1 < keyValues.length; i += 2) builder.queryParam(keyValues[i], keyValues[i + 1]);
        URI uri = builder.build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, uri.toString()).build();
    }
}
