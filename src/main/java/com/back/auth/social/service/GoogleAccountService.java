package com.back.auth.social.service;

import com.back.auth.core.TokenIssuer;
import com.back.auth.core.dto.AuthResponse;
import com.back.auth.core.mapper.AuthMapper;
import com.back.auth.social.dto.GoogleLinkStatusResponse;
import com.back.auth.social.dto.GooglePendingLinkResponse;
import com.back.auth.social.dto.UserSocialAccount;
import com.back.auth.social.exception.GoogleAccountNotLinkedException;
import com.back.auth.social.mapper.SocialAccountMapper;
import com.back.global.oauth.GoogleIntegrationException;
import com.back.global.oauth.GoogleOAuthClient;
import com.back.global.oauth.GoogleProperties;
import com.back.global.oauth.OAuthProvider;
import com.back.global.oauth.OAuthStateService;
import com.back.global.oauth.dto.GoogleTokenResponse;
import com.back.global.oauth.dto.GoogleUserInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * 구글 소셜 로그인 및 계정 연결 관리.
 *
 * 구글만으로는 신규 가입을 받지 않는다 — users 의 학번·전화·전공이 NOT NULL/UNIQUE 이고,
 * 동아리 회원 확인이 필요한 서비스라 구글 계정만으로 회원을 만들 수 없다.
 *
 * 연결 경로는 둘이다:
 *  1) 마이페이지에서 [구글 계정 연결] — 로그인 상태에서 동의 화면을 거쳐 붙인다
 *  2) 로그인 화면에서 [구글로 로그인] 했는데 연결된 회원이 없을 때 — 구글 계정을 잠시 보관하고(PendingLinkStore)
 *     같은 이메일 회원이 있으면 "이미 가입된 계정이에요 — 연결할까요?", 없으면 "회원가입으로 진행할까요?" 를 묻는다.
 *     어느 쪽이든 아이디·비밀번호 로그인이 끝나면 그 회원에 붙인다 (completePendingLink)
 *
 * 저장은 provider 중립 테이블(user_social_accounts)에 하고 이 서비스는 provider=GOOGLE 만 다룬다.
 * 카카오·네이버가 들어오면 provider 별 클라이언트를 주입받는 형태로 일반화하면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAccountService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 동의 화면 URL 과 거기 실린 state — 컨트롤러가 state 를 쿠키로 브라우저에 묶는 데 쓴다 */
    public record Authorize(String url, String state) {
    }

    private final GoogleProperties properties;
    private final GoogleOAuthClient oauthClient;
    private final OAuthStateService stateService;
    private final SocialAccountMapper accountMapper;
    private final AuthMapper authMapper;
    private final PendingLinkStore pendingLinkStore;
    private final TokenIssuer tokenIssuer;

    /** 로그인 동의 화면 URL. */
    public Authorize buildLoginAuthorize() {
        String state = stateService.issue(OAuthStateService.PURPOSE_LOGIN, null);
        String url = oauthClient.buildAuthorizeUrl(
                GoogleProperties.LOGIN_SCOPE, properties.getLoginRedirectUri(), state, false);
        return new Authorize(url, state);
    }

    /** 계정 연결 동의 화면 URL (로그인 상태에서 호출). */
    public Authorize buildLinkAuthorize(Long userId) {
        String state = stateService.issue(OAuthStateService.PURPOSE_LINK, userId);
        String url = oauthClient.buildAuthorizeUrl(
                GoogleProperties.LOGIN_SCOPE, properties.getLoginRedirectUri(), state, false);
        return new Authorize(url, state);
    }

    /**
     * 구글 로그인 처리 (state 는 콜백 컨트롤러가 이미 검증했다).
     *
     * 연결된 회원이 없으면 {@link GoogleAccountNotLinkedException} 을 던진다. 실패로 끝내는 게 아니라
     * 구글 계정을 잠시 보관해 두고, 사용자가 아이디·비밀번호로 로그인하면 붙여 주는 다음 단계로 이어진다.
     */
    @Transactional
    public AuthResponse loginWithGoogle(String code, HttpServletResponse response) {
        GoogleUserInfo info = exchangeAndParse(code, properties.getLoginRedirectUri());

        UserSocialAccount account = accountMapper.findByProviderUser(OAuthProvider.GOOGLE, info.getSub());
        if (account == null) {
            // 같은 이메일로 가입된 회원이 있으면 그 아이디를 함께 보관한다 — 안내 화면이
            // "이미 가입된 계정(wm***6)이에요" 라고 짚어 줄 수 있게. 있어도 자동으로 붙이지는 않는다:
            // 비밀번호 로그인을 거치지 않으면 재할당된 이메일로 남의 계정을 가져갈 수 있다.
            String matchedLoginId = authMapper.findLoginIdByEmail(info.getEmail());
            String token = pendingLinkStore.issue(info.getSub(), info.getEmail(), matchedLoginId);
            log.info("구글 로그인 - 미연결 계정, 연결 대기 발급 (emailMatched={})", matchedLoginId != null);
            throw new GoogleAccountNotLinkedException(token, matchedLoginId != null, maskEmail(info.getEmail()));
        }

        // 자동 로그인은 소셜 로그인에서 기본 적용한다 — 구글로 들어오는 사용자는 앱처럼 쓰길 기대한다
        return tokenIssuer.issueForUserId(account.getUserId(), true, response);
    }

    /** 구글 계정을 회원 계정에 연결한다 (마이페이지 경로. state 는 콜백 컨트롤러가 이미 검증했다). */
    @Transactional
    public String linkAccount(Long userId, String code) {
        GoogleUserInfo info = exchangeAndParse(code, properties.getLoginRedirectUri());
        link(userId, info.getSub(), info.getEmail());
        return info.getEmail();
    }

    /**
     * 로그인 화면 경로의 연결 마무리 — 아이디·비밀번호 로그인이 끝난 회원에게 보관해 둔 구글 계정을 붙인다.
     * 토큰은 컨트롤러가 HttpOnly 쿠키에서 꺼내 준다(연결 대기를 만든 브라우저만 마무리할 수 있게).
     */
    @Transactional
    public String completePendingLink(Long userId, String token) {
        PendingLinkStore.PendingLink pending = pendingLinkStore.consume(token);
        link(userId, pending.sub(), pending.email());
        return pending.email();
    }

    /**
     * 연결 대기 안내에 쓸 정보 (로그인·회원가입 화면). 대기가 없거나 만료됐으면 null.
     * 소비하지 않는다 — 두 화면이 번갈아 읽는다.
     */
    public GooglePendingLinkResponse getPendingLink(String token) {
        PendingLinkStore.PendingLink pending = pendingLinkStore.peek(token);
        if (pending == null) {
            return null;
        }
        return new GooglePendingLinkResponse(true, pending.email(), maskEmail(pending.email()),
                pending.matchedLoginId() != null, maskLoginId(pending.matchedLoginId()));
    }

    /** 사용자가 연결하지 않겠다고 한 경우 — 대기 정보를 지운다. */
    public void discardPendingLink(String token) {
        pendingLinkStore.discard(token);
    }

    private void link(Long userId, String sub, String email) {
        // 같은 구글 계정을 두 회원이 나눠 쓰면 로그인 시 누구인지 정할 수 없다
        UserSocialAccount existing = accountMapper.findByProviderUser(OAuthProvider.GOOGLE, sub);
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new GoogleIntegrationException("이미 다른 회원에게 연결된 구글 계정입니다.", HttpStatus.CONFLICT);
        }

        UserSocialAccount account = new UserSocialAccount();
        account.setUserId(userId);
        account.setProvider(OAuthProvider.GOOGLE);
        account.setProviderUserId(sub);
        account.setProviderEmail(email);
        accountMapper.upsertLink(account);

        log.info("구글 계정 연결 완료 - userId={}, email={}", userId, email);
    }

    public GoogleLinkStatusResponse getLinkStatus(Long userId) {
        UserSocialAccount account = accountMapper.findByUserAndProvider(userId, OAuthProvider.GOOGLE);
        if (account == null) {
            return GoogleLinkStatusResponse.notLinked();
        }
        return new GoogleLinkStatusResponse(
                true,
                account.getProviderEmail(),
                account.getLinkedAt() == null ? null : account.getLinkedAt().format(FORMATTER)
        );
    }

    /**
     * 연결 해제 — 이후 구글로 로그인할 수 없다.
     *
     * 캘린더 연동은 건드리지 않는다. 별도 동의로 받은 별도 기능이고 테이블도 분리돼 있어,
     * 로그인 수단을 정리했다고 캘린더에 들어가던 일정까지 말없이 끊으면 사용자가 이유를 알 수 없다.
     * 캘린더는 마이페이지의 캘린더 토글로 따로 해제한다.
     */
    @Transactional
    public void unlinkAccount(Long userId) {
        int deleted = accountMapper.deleteByUserAndProvider(userId, OAuthProvider.GOOGLE);
        if (deleted == 0) {
            throw new GoogleIntegrationException("연결된 구글 계정이 없습니다.", HttpStatus.NOT_FOUND);
        }
        log.info("구글 계정 연결 해제 - userId={}", userId);
    }

    private GoogleUserInfo exchangeAndParse(String code, String redirectUri) {
        GoogleTokenResponse token = oauthClient.exchangeCode(code, redirectUri);
        GoogleUserInfo info = oauthClient.parseIdToken(token.getIdToken());

        // 미인증 이메일은 소유가 증명되지 않은 값이라 계정 식별 근거로 쓰지 않는다.
        // (카카오처럼 이메일이 선택 동의인 provider 를 붙일 때는 이 검사를 provider 별로 갈라야 한다)
        if (!info.isEmailVerified()) {
            throw new GoogleIntegrationException("이메일이 확인되지 않은 구글 계정입니다.", HttpStatus.BAD_REQUEST);
        }
        return info;
    }

    /** hong.gildong@gmail.com → h***g@gmail.com. 어느 계정인지 알아볼 만큼만 남긴다. */
    static String maskEmail(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    /** wm5256 → wm***6. 본인은 알아보고 남은 못 맞히게 앞 둘·끝 하나만 남긴다. */
    static String maskLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return null;
        }
        if (loginId.length() <= 3) {
            return loginId.charAt(0) + "***";
        }
        return loginId.substring(0, 2) + "***" + loginId.charAt(loginId.length() - 1);
    }
}
