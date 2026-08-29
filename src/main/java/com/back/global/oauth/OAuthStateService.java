package com.back.global.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * OAuth state 발급·검증.
 *
 * state 가 필요한 이유는 두 가지다.
 *  1) CSRF 방어 — 공격자가 자기 구글 계정의 code 로 콜백을 호출해 피해자 계정에 붙이는 것을 막는다.
 *  2) 사용자 식별 — 캘린더 콜백은 구글이 브라우저 리다이렉트로 부르기 때문에 Authorization 헤더가 없다.
 *     누구의 연동인지 알려면 state 에 담아 두는 수밖에 없다.
 *
 * 한 번 쓴 state 는 즉시 삭제한다(재사용 차단).
 */
@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private static final String KEY_PREFIX = "google:state:";
    private static final long TTL_SECONDS = 300; // 5분 — 동의 화면을 넘기기에 충분하고, 방치된 state 는 빨리 죽는 게 낫다

    public static final String PURPOSE_LOGIN = "login";
    public static final String PURPOSE_LINK = "link";
    public static final String PURPOSE_CALENDAR = "calendar";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param userId 로그인 목적이면 null (아직 누군지 모른다)
     * @return 동의 화면에 실어 보낼 state
     */
    public String issue(String purpose, Long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redisTemplate.opsForValue().set(
                KEY_PREFIX + state,
                purpose + ":" + (userId == null ? "" : userId),
                TTL_SECONDS, TimeUnit.SECONDS
        );
        return state;
    }

    /** state 에 담아둔 내용. */
    public record StateData(String purpose, Long userId) {
    }

    /**
     * state 를 검증하고 소비한다.
     * 로그인과 계정 연결이 같은 콜백을 쓰므로, 콜백은 목적을 미리 알 수 없다 — 여기서 꺼내 분기한다.
     */
    public StateData consume(String state) {
        if (state == null || state.isBlank()) {
            throw new GoogleIntegrationException("잘못된 요청입니다. (state 누락)", HttpStatus.BAD_REQUEST);
        }

        String key = KEY_PREFIX + state;
        String stored = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key); // 성공이든 실패든 재사용은 막는다

        if (stored == null) {
            throw new GoogleIntegrationException("인증 요청이 만료되었습니다. 다시 시도해주세요.", HttpStatus.BAD_REQUEST);
        }

        String[] parts = stored.split(":", 2);
        String userId = parts.length > 1 ? parts[1] : "";
        return new StateData(parts[0], userId.isBlank() ? null : Long.parseLong(userId));
    }

    /**
     * 목적이 정해진 곳에서 쓰는 버전.
     *
     * @return 발급 시 담아둔 userId (로그인 목적이면 null)
     */
    public Long consume(String state, String expectedPurpose) {
        StateData data = consume(state);
        if (!expectedPurpose.equals(data.purpose())) {
            throw new GoogleIntegrationException("잘못된 인증 요청입니다.", HttpStatus.BAD_REQUEST);
        }
        return data.userId();
    }
}
