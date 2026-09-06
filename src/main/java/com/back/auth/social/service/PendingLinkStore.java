package com.back.auth.social.service;

import com.back.global.oauth.GoogleIntegrationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * "구글로 로그인했는데 연결된 회원이 없다" 순간의 구글 계정 정보를 잠시 보관한다.
 *
 * 그 자리에서 바로 회원을 만들 수 없으니(학번·전화가 필수) 사용자를 로그인 화면으로 보내
 *  - 같은 이메일로 가입된 회원이 있으면 "이미 가입된 계정이에요 — 연결할까요?" 를,
 *  - 없으면 "회원가입으로 진행할까요?" 를 묻고,
 * 어느 길이든 아이디·비밀번호 로그인이 끝나는 순간 여기 보관해 둔 구글 계정을 그 회원에 붙인다.
 * 비밀번호 로그인을 거치게 하는 것이 핵심이다 — 이메일이 같다는 이유만으로 붙이면
 * 해지 후 재할당된 이메일로 남의 계정을 가져갈 수 있다.
 *
 * 토큰은 10분이면 사라지고, 연결에 쓰이면(consume) 그 자리에서 지운다. 안내 화면이 내용을 읽는 것(peek)은
 * 소비가 아니다 — 로그인 화면과 회원가입 화면이 번갈아 읽어야 하기 때문이다.
 * 브라우저 바인딩은 OAuthStateCookie 가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class PendingLinkStore {

    /** matchedLoginId: 구글 이메일과 같은 이메일로 가입된 회원의 아이디. 없으면 null. */
    public record PendingLink(String sub, String email, String matchedLoginId) {
    }

    private static final String KEY_PREFIX = "google:pending-link:";
    private static final long TTL_SECONDS = 600;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public String issue(String sub, String email, String matchedLoginId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redisTemplate.opsForValue().set(KEY_PREFIX + token, write(new PendingLink(sub, email, matchedLoginId)),
                TTL_SECONDS, TimeUnit.SECONDS);
        return token;
    }

    /** 안내 화면용 — 소비하지 않고 읽는다. 없거나 만료됐으면 null. */
    public PendingLink peek(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String stored = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        return stored == null ? null : read(stored);
    }

    /** 연결에 쓴다 — 검증하고 소비한다. 없거나 만료됐으면 예외. */
    public PendingLink consume(String token) {
        if (token == null || token.isBlank()) {
            throw new GoogleIntegrationException("연결 대기 중인 구글 계정이 없습니다. 구글 로그인을 다시 시도해주세요.",
                    HttpStatus.BAD_REQUEST);
        }
        String key = KEY_PREFIX + token;
        String stored = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);

        if (stored == null) {
            throw new GoogleIntegrationException("연결 요청이 만료되었습니다. 구글 로그인을 다시 시도해주세요.",
                    HttpStatus.BAD_REQUEST);
        }
        return read(stored);
    }

    /** 사용자가 "연결하지 않겠다"고 한 경우. 남겨 두면 10분 안에 다른 계정으로 로그인할 때 붙어 버린다. */
    public void discard(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + token);
        }
    }

    private String write(PendingLink link) {
        try {
            return objectMapper.writeValueAsString(link);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("구글 연결 대기 정보를 저장할 수 없습니다.", e);
        }
    }

    private PendingLink read(String json) {
        try {
            return objectMapper.readValue(json, PendingLink.class);
        } catch (JsonProcessingException e) {
            throw new GoogleIntegrationException("연결 요청 정보를 읽을 수 없습니다. 구글 로그인을 다시 시도해주세요.",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
