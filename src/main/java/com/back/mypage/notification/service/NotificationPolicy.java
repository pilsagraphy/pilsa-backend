package com.back.mypage.notification.service;

import com.back.mypage.notification.dto.NotificationType;
import com.back.mypage.notification.mapper.NotificationPolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 알림 발행 스위치 — 관리자가 '운영 관리 > 알림 설정' 에서 사건별로 켜고 끈다 (policy_settings notify_*).
 *
 * 행이 없거나 조회에 실패하면 <b>보내는 쪽</b>이 기본이다 — 설정 표가 비었다고 알림이 조용히 끊기면 안 된다.
 * 발행은 댓글 하나에 수신자 여러 명이라 같은 키를 여러 번 읽는다 — 짧게(30초) 캐시한다. 관리자가 바꾸면 늦어도 30초 안에 반영.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPolicy {

    private static final long CACHE_MS = 30_000L;

    private final NotificationPolicyMapper mapper;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** 이 유형의 알림을 지금 보내는가 */
    public boolean isEnabled(NotificationType type) {
        String code = codeFor(type);
        if (code == null) {
            return true; // 스위치가 없는 유형(신고 처리·제재 등)은 항상 보낸다
        }
        long now = System.currentTimeMillis();
        Cached c = cache.get(code);
        if (c != null && now - c.at < CACHE_MS) {
            return c.enabled;
        }
        boolean enabled = true;
        try {
            String value = mapper.findSettingValue(code);
            enabled = value == null || !value.trim().equals("0");
        } catch (Exception e) {
            log.warn("알림 스위치 조회 실패 - code={}, 기본(보냄) 적용, 원인={}", code, e.getMessage());
        }
        cache.put(code, new Cached(enabled, now));
        return enabled;
    }

    /** 유형 → policy_settings 키. 스위치를 둔 네 가지만 */
    static String codeFor(NotificationType type) {
        return switch (type) {
            case COMMENT -> "notify_comment";
            case REPLY -> "notify_reply";
            case PINNED_POST -> "notify_pinned_post";
            case EVENT -> "notify_event";
            default -> null;
        };
    }

    private record Cached(boolean enabled, long at) { }
}
