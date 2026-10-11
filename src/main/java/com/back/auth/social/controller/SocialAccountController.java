package com.back.auth.social.controller;

import com.back.auth.social.dto.GoogleLinkStatusResponse;
import com.back.auth.social.service.SocialAccountService;
import com.back.global.oauth.GoogleIntegrationException;
import com.back.global.oauth.OAuthProvider;
import com.back.global.oauth.OAuthStateCookie;
import com.back.global.security.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 마이페이지 — 네이버·카카오 계정 연결 (GoogleAccountController 와 같은 모양). {provider} 는 naver | kakao */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/mypage/social")
@Tag(name = "마이페이지-소셜 계정 연결", description = "네이버·카카오 계정 연결/해제. 구글은 /api/user/mypage/google")
public class SocialAccountController {

    private final SocialAccountService accountService;
    private final OAuthStateCookie stateCookie;

    @Operation(summary = "연결 상태", description = "{linked, googleEmail(=제공자 이메일, 없으면 null), linkedAt, configured}")
    @GetMapping("/{provider}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String provider) {
        String p = requireProvider(provider);
        GoogleLinkStatusResponse s = accountService.getLinkStatus(p, AuthUtils.currentUserId());
        return ResponseEntity.ok(Map.of(
                "linked", s.isLinked(),
                "email", s.getGoogleEmail() == null ? "" : s.getGoogleEmail(),
                "linkedAt", s.getLinkedAt() == null ? "" : s.getLinkedAt(),
                "configured", accountService.isConfigured(p)
        ));
    }

    @Operation(summary = "계정 연결 - 동의 화면 URL 발급", description = "돌아올 때 /mypage?naver=linked|already_linked|failed")
    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Map<String, String>> authorize(@PathVariable String provider, HttpServletResponse response) {
        String p = requireProvider(provider);
        SocialAccountService.Authorize authorize = accountService.buildLinkAuthorize(p, AuthUtils.currentUserId());
        stateCookie.bindState(response, authorize.state());
        return ResponseEntity.ok(Map.of("authorizeUrl", authorize.url()));
    }

    @Operation(summary = "계정 연결 마무리 (로그인 화면 경로)", description = "소셜 로그인 뒤 미연결로 돌아와 아이디·비밀번호 로그인이 끝나면 호출. 쿠키의 대기 토큰으로 붙인다")
    @PostMapping("/{provider}/link")
    public ResponseEntity<Map<String, String>> completeLink(@PathVariable String provider, HttpServletRequest request, HttpServletResponse response) {
        String p = requireProvider(provider);
        String email = accountService.completePendingLink(p, AuthUtils.currentUserId(), stateCookie.readPendingLink(request));
        stateCookie.clearPendingLink(response);
        return ResponseEntity.ok(Map.of("message", OAuthProvider.label(p) + " 계정을 연결했습니다.", "email", email == null ? "" : email));
    }

    @Operation(summary = "계정 연결 해제")
    @DeleteMapping("/{provider}")
    public ResponseEntity<Map<String, String>> unlink(@PathVariable String provider) {
        String p = requireProvider(provider);
        accountService.unlinkAccount(p, AuthUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", OAuthProvider.label(p) + " 계정 연결을 해제했습니다."));
    }

    private String requireProvider(String provider) {
        String p = OAuthProvider.fromPath(provider);
        if (p == null) throw new GoogleIntegrationException("지원하지 않는 로그인 방식입니다.", HttpStatus.NOT_FOUND);
        return p;
    }
}
