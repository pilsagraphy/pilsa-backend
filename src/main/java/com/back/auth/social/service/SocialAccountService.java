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
import com.back.global.oauth.OAuthProvider;
import com.back.global.oauth.OAuthStateService;
import com.back.global.oauth.SocialOAuthClient;
import com.back.global.oauth.dto.SocialUserInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 네이버·카카오 소셜 로그인 및 계정 연결 — GoogleAccountService 와 같은 흐름을 provider 별 클라이언트로 일반화한 것 (PM 2026-10-11).
 *
 * 소셜 계정만으로는 가입되지 않는다(학번·전화가 필수). 연결 경로는 둘:
 *  1) 마이페이지 [연결] — 로그인 상태에서 동의를 거쳐 붙인다
 *  2) 로그인 화면 [네이버/카카오로 로그인] 했는데 연결된 회원이 없을 때 — PendingLinkStore 에 잠시 보관하고,
 *     아이디·비밀번호 로그인이 끝나면 그 회원에 붙인다 (completePendingLink). 대기 쿠키(g_pending_link)는 구글과 같이 쓴다.
 * 저장은 user_social_accounts(provider 열) 그대로.
 */
@Slf4j
@Service
public class SocialAccountService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record Authorize(String url, String state) {
    }

    private final Map<String, SocialOAuthClient> clients;
    private final OAuthStateService stateService;
    private final SocialAccountMapper accountMapper;
    private final AuthMapper authMapper;
    private final PendingLinkStore pendingLinkStore;
    private final TokenIssuer tokenIssuer;
    private final com.back.mypage.calendar.service.NaverCalendarService naverCalendarService;
    private final com.back.mypage.calendar.service.KakaoCalendarService kakaoCalendarService;

    public SocialAccountService(List<SocialOAuthClient> clientList, OAuthStateService stateService, SocialAccountMapper accountMapper,
                                AuthMapper authMapper, PendingLinkStore pendingLinkStore, TokenIssuer tokenIssuer,
                                com.back.mypage.calendar.service.NaverCalendarService naverCalendarService,
                                com.back.mypage.calendar.service.KakaoCalendarService kakaoCalendarService) {
        this.clients = clientList.stream().collect(Collectors.toMap(SocialOAuthClient::provider, Function.identity()));
        this.stateService = stateService;
        this.accountMapper = accountMapper;
        this.authMapper = authMapper;
        this.pendingLinkStore = pendingLinkStore;
        this.tokenIssuer = tokenIssuer;
        this.naverCalendarService = naverCalendarService;
        this.kakaoCalendarService = kakaoCalendarService;
    }

    /** 키가 아직 properties 에 없으면 503 — 버튼은 보이되 "준비 중" 으로 안내한다 */
    public SocialOAuthClient client(String provider) {
        SocialOAuthClient c = clients.get(provider);
        if (c == null) throw new GoogleIntegrationException("지원하지 않는 로그인 방식입니다.", HttpStatus.NOT_FOUND);
        if (!c.isConfigured()) {
            throw new GoogleIntegrationException(OAuthProvider.label(provider) + " 로그인은 아직 준비 중이에요.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return c;
    }

    public boolean isConfigured(String provider) {
        SocialOAuthClient c = clients.get(provider);
        return c != null && c.isConfigured();
    }

    public Authorize buildLoginAuthorize(String provider) {
        SocialOAuthClient c = client(provider);
        String state = stateService.issue(OAuthStateService.PURPOSE_LOGIN, null);
        return new Authorize(c.buildAuthorizeUrl(state), state);
    }

    public Authorize buildLinkAuthorize(String provider, Long userId) {
        SocialOAuthClient c = client(provider);
        String state = stateService.issue(OAuthStateService.PURPOSE_LINK, userId);
        return new Authorize(c.buildAuthorizeUrl(state), state);
    }

    /** 로그인 — 연결된 회원이 없으면 GoogleAccountNotLinkedException(연결 대기) */
    @Transactional
    public AuthResponse login(String provider, String code, String state, HttpServletResponse response) {
        SocialUserInfo info = client(provider).fetchUser(code, state);
        UserSocialAccount account = accountMapper.findByProviderUser(provider, info.getId());
        if (account == null) {
            String matchedLoginId = info.getEmail() == null ? null : authMapper.findLoginIdByEmail(info.getEmail());
            String token = pendingLinkStore.issue(provider, info.getId(), info.getEmail(), matchedLoginId);
            log.info("{} 로그인 - 미연결 계정, 연결 대기 발급 (emailMatched={})", provider, matchedLoginId != null);
            throw new GoogleAccountNotLinkedException(token, matchedLoginId != null, GoogleAccountService.maskEmail(info.getEmail()));
        }
        return tokenIssuer.issueForUserId(account.getUserId(), true, response);
    }

    @Transactional
    public String linkAccount(String provider, Long userId, String code, String state) {
        SocialUserInfo info = client(provider).fetchUser(code, state);
        link(provider, userId, info.getId(), info.getEmail());
        return info.getEmail();
    }

    /** 로그인 화면 경로의 연결 마무리 — 보관해 둔 계정의 provider 가 요청 provider 와 같아야 한다 */
    @Transactional
    public String completePendingLink(String provider, Long userId, String token) {
        PendingLinkStore.PendingLink pending = pendingLinkStore.consume(token);
        if (!provider.equals(pending.providerOrGoogle())) {
            throw new GoogleIntegrationException("연결 대기 중인 " + OAuthProvider.label(provider) + " 계정이 없습니다.", HttpStatus.BAD_REQUEST);
        }
        link(provider, userId, pending.sub(), pending.email());
        return pending.email();
    }

    private void link(String provider, Long userId, String providerUserId, String email) {
        UserSocialAccount existing = accountMapper.findByProviderUser(provider, providerUserId);
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new GoogleIntegrationException("이미 다른 회원에게 연결된 " + OAuthProvider.label(provider) + " 계정입니다.", HttpStatus.CONFLICT);
        }
        UserSocialAccount account = new UserSocialAccount();
        account.setUserId(userId);
        account.setProvider(provider);
        account.setProviderUserId(providerUserId);
        account.setProviderEmail(email);
        accountMapper.upsertLink(account);
        log.info("{} 계정 연결 완료 - userId={}, email={}", provider, userId, email);
    }

    public GoogleLinkStatusResponse getLinkStatus(String provider, Long userId) {
        UserSocialAccount account = accountMapper.findByUserAndProvider(userId, provider);
        if (account == null) return GoogleLinkStatusResponse.notLinked();
        return new GoogleLinkStatusResponse(true, account.getProviderEmail(),
                account.getLinkedAt() == null ? null : account.getLinkedAt().format(FORMATTER));
    }

    public GooglePendingLinkResponse getPendingLink(String token) {
        PendingLinkStore.PendingLink pending = pendingLinkStore.peek(token);
        if (pending == null) return null;
        return new GooglePendingLinkResponse(pending.providerOrGoogle(), true, pending.email(), GoogleAccountService.maskEmail(pending.email()),
                pending.matchedLoginId() != null, GoogleAccountService.maskLoginId(pending.matchedLoginId()));
    }

    public void discardPendingLink(String token) {
        pendingLinkStore.discard(token);
    }

    @Transactional
    public void unlinkAccount(String provider, Long userId) {
        if (accountMapper.deleteByUserAndProvider(userId, provider) == 0) {
            throw new GoogleIntegrationException("연결된 " + OAuthProvider.label(provider) + " 계정이 없습니다.", HttpStatus.NOT_FOUND);
        }
        log.info("{} 계정 연결 해제 - userId={}", provider, userId);
        // 그 계정의 캘린더 연동도 함께 끊는다 (PM 2026-10-13). 톡캘린더에 넣어 둔 일정은 남긴다
        try {
            if (OAuthProvider.NAVER.equals(provider) && Boolean.TRUE.equals(naverCalendarService.getStatus(userId).get("linked"))) naverCalendarService.unlink(userId);
            if (OAuthProvider.KAKAO.equals(provider) && Boolean.TRUE.equals(kakaoCalendarService.getStatus(userId).get("linked"))) kakaoCalendarService.unlink(userId, false);
        } catch (Exception e) {
            log.warn("{} 계정 해제에 딸린 캘린더 해제 실패 - userId={}, {}", provider, userId, e.getMessage());
        }
    }

    /** 제공자 쪽에서 연결을 끊었다는 통보(카카오 웹훅·네이버 연결 끊기 콜백) — 우리 쪽 연결도 지운다 */
    @Transactional
    public int unlinkByProviderUser(String provider, String providerUserId) {
        int n = accountMapper.deleteByProviderUser(provider, providerUserId);
        log.info("{} 쪽 연결 해제 통보 - providerUserId={}, 지운 연결 {}건", provider, providerUserId, n);
        return n;
    }
}
