package com.back.auth.social.service;

import com.back.auth.core.dto.AuthResponse;
import com.back.auth.core.TokenIssuer;
import com.back.auth.social.dto.GoogleLinkStatusResponse;
import com.back.auth.social.dto.UserSocialAccount;
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
 * 따라서 흐름은 "기존 계정으로 가입 → 마이페이지에서 구글 연결 → 이후 구글로 로그인" 이다.
 *
 * 저장은 provider 중립 테이블(user_social_accounts)에 하고 이 서비스는 provider=GOOGLE 만 다룬다.
 * 카카오·네이버가 들어오면 provider 별 클라이언트를 주입받는 형태로 일반화하면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAccountService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GoogleProperties properties;
    private final GoogleOAuthClient oauthClient;
    private final OAuthStateService stateService;
    private final SocialAccountMapper accountMapper;
    private final TokenIssuer tokenIssuer;

    /** 로그인 동의 화면 URL. */
    public String buildLoginAuthorizeUrl() {
        String state = stateService.issue(OAuthStateService.PURPOSE_LOGIN, null);
        return oauthClient.buildAuthorizeUrl(
                GoogleProperties.LOGIN_SCOPE, properties.getLoginRedirectUri(), state, false);
    }

    /** 계정 연결 동의 화면 URL (로그인 상태에서 호출). */
    public String buildLinkAuthorizeUrl(Long userId) {
        String state = stateService.issue(OAuthStateService.PURPOSE_LINK, userId);
        return oauthClient.buildAuthorizeUrl(
                GoogleProperties.LOGIN_SCOPE, properties.getLoginRedirectUri(), state, false);
    }

    /**
     * 구글 로그인 처리 (state 는 콜백 컨트롤러가 이미 검증했다).
     *
     * 연결된 계정이 없으면 409 로 돌려보낸다 — 프론트는 이때 "먼저 로그인 후 마이페이지에서
     * 구글 계정을 연결해주세요" 를 안내한다.
     */
    @Transactional
    public AuthResponse loginWithGoogle(String code, HttpServletResponse response) {
        GoogleUserInfo info = exchangeAndParse(code, properties.getLoginRedirectUri());

        UserSocialAccount account = accountMapper.findByProviderUser(OAuthProvider.GOOGLE, info.getSub());
        if (account == null) {
            throw new GoogleIntegrationException(
                    "연결된 계정이 없습니다. 로그인 후 마이페이지에서 구글 계정을 연결해주세요.", HttpStatus.CONFLICT);
        }

        // 자동 로그인은 소셜 로그인에서 기본 적용한다 — 구글로 들어오는 사용자는 앱처럼 쓰길 기대한다
        return tokenIssuer.issueForUserId(account.getUserId(), true, response);
    }

    /** 구글 계정을 회원 계정에 연결한다 (state 는 콜백 컨트롤러가 이미 검증했다). */
    @Transactional
    public String linkAccount(Long userId, String code) {
        GoogleUserInfo info = exchangeAndParse(code, properties.getLoginRedirectUri());

        // 같은 구글 계정을 두 회원이 나눠 쓰면 로그인 시 누구인지 정할 수 없다
        UserSocialAccount existing = accountMapper.findByProviderUser(OAuthProvider.GOOGLE, info.getSub());
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new GoogleIntegrationException("이미 다른 회원에게 연결된 구글 계정입니다.", HttpStatus.CONFLICT);
        }

        UserSocialAccount account = new UserSocialAccount();
        account.setUserId(userId);
        account.setProvider(OAuthProvider.GOOGLE);
        account.setProviderUserId(info.getSub());
        account.setProviderEmail(info.getEmail());
        accountMapper.upsertLink(account);

        log.info("구글 계정 연결 완료 - userId={}, email={}", userId, info.getEmail());
        return info.getEmail();
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
}
