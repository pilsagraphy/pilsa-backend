package com.back.auth.social.service;

import com.back.auth.core.TokenIssuer;
import com.back.auth.core.dto.AuthResponse;
import com.back.auth.core.mapper.AuthMapper;
import com.back.auth.local.dto.SignupRequest;
import com.back.auth.local.service.AuthService;
import com.back.global.oauth.GoogleIntegrationException;
import com.back.global.oauth.OAuthProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 없는 소셜 가입 (PM 2026-10-13 — 네이버 검수 '신규 회원가입 적용' 을 위해).
 *
 * 흐름: 로그인 화면 [네이버/카카오/구글로 로그인] → 연결된 회원 없음 → 대기 쿠키(g_pending_link)를 들고 회원가입 화면
 *      → 비밀번호 칸 없이 가입 → 여기서 (1) users 행 생성(password_hash = SOCIAL_ONLY) (2) 대기 중인 소셜 계정을 붙이고
 *      (3) 바로 로그인 토큰 발급. 한 트랜잭션이라 연결에 실패하면 users 행도 남지 않는다.
 * 아이디는 사용자가 직접 정한다(서버 자동 생성 아님 — PM 결정). 이메일 인증번호도 보통 가입과 똑같이 거친다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSignupService {

    private final AuthService authService;
    private final PendingLinkStore pendingLinkStore;
    private final GoogleAccountService googleAccountService;
    private final SocialAccountService socialAccountService;
    private final AuthMapper authMapper;
    private final com.back.auth.social.mapper.SocialAccountMapper accountMapper;
    private final TokenIssuer tokenIssuer;

    public record Result(AuthResponse auth, String provider) {
    }

    @Transactional
    public Result signup(SignupRequest request, String pendingToken, HttpServletResponse response) {
        PendingLinkStore.PendingLink pending = pendingToken == null ? null : pendingLinkStore.peek(pendingToken);
        if (pending == null) {
            throw new GoogleIntegrationException("간편 로그인 정보가 만료됐어요. 다시 로그인해 주세요.", HttpStatus.BAD_REQUEST);
        }
        String provider = pending.providerOrGoogle();

        // 10분 사이 같은 소셜 계정이 다른 회원에게 붙었으면 여기서 막는다 — users INSERT 뒤에 걸리면 행은 롤백돼도
        // 이메일 인증 플래그·대기 토큰(Redis)은 이미 소진돼 사용자가 둘 다 다시 해야 한다
        if (accountMapper.findByProviderUser(provider, pending.sub()) != null) {
            throw new GoogleIntegrationException("이미 다른 회원에게 연결된 " + OAuthProvider.label(provider) + " 계정이에요.", HttpStatus.CONFLICT);
        }

        Long userId = authService.signupSocialOnly(request);

        if (OAuthProvider.GOOGLE.equals(provider)) {
            googleAccountService.completePendingLink(userId, pendingToken);
        } else {
            socialAccountService.completePendingLink(provider, userId, pendingToken);
        }

        // 소셜 로그인과 같은 기준으로 자동 로그인 — 가입 직후 바로 쓰게 한다
        AuthResponse auth = tokenIssuer.issueForUserId(userId, true, response);
        authMapper.updateLastLoginAtByUserId(userId);
        log.info("{} 소셜 가입(비밀번호 없음) 완료 - userId={}", provider, userId);
        return new Result(auth, provider);
    }
}
