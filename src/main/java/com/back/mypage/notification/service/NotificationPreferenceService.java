package com.back.mypage.notification.service;

import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.back.mypage.notification.dto.NotificationType;
import com.back.mypage.notification.mapper.NotificationPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 회원이 직접 정하는 알림 수신 범위 (PM 2026-10-10).
 *
 *  - 유형 스위치: 마이페이지 설정에서 댓글 / 답글 / 중요 글 / 일정 을 켜고 끈다. 관리자 알림 설정(policy_settings notify_*)과
 *    같은 네 종류이고, 관리자가 끈 유형은 회원 설정과 무관하게 아무에게도 가지 않는다 ({@link NotificationPublisher}).
 *  - 대상별 끄기: 내 글의 댓글 알림(post), 내 댓글의 답글 알림(comment)을 그 건만 끈다. 판정은 발행 호출부(BoardServiceImpl)가 한다 —
 *    publish() 는 "이동 대상" 만 받아서 어느 댓글에 달린 답글인지 모른다.
 *
 * 행이 없으면 받는다. 그래서 설정을 안 만진 회원은 지금까지와 똑같이 모든 알림을 받는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    /** 회원이 고를 수 있는 유형 — 신고 처리·제재 알림은 끌 수 없다 (운영 통지) */
    public static final List<NotificationType> USER_TYPES =
            List.of(NotificationType.COMMENT, NotificationType.REPLY, NotificationType.PINNED_POST, NotificationType.EVENT);

    private static final Set<String> MUTE_TARGETS = Set.of("post", "comment");

    private final NotificationPreferenceMapper mapper;

    /** 발행 지점용 — 수신자가 이 유형을 껐으면 false. 회원이 고를 수 없는 유형(신고·제재)은 항상 true */
    public boolean isTypeEnabled(Long userId, NotificationType type) {
        if (!USER_TYPES.contains(type)) return true;
        return !mapper.findDisabledTypes(userId).contains(type.name());
    }

    /** 발행 지점용 — 수신자가 이 글/댓글의 알림을 껐는가 */
    public boolean isMuted(Long userId, String targetType, Long targetId) {
        return mapper.existsMute(userId, targetType, targetId);
    }

    /** 내 설정 — { COMMENT: true, REPLY: true, PINNED_POST: false, EVENT: true } */
    public Map<String, Boolean> mySettings() {
        Long userId = AuthUtils.currentUserId();
        Set<String> disabled = Set.copyOf(mapper.findDisabledTypes(userId));
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (NotificationType type : USER_TYPES) {
            result.put(type.name(), !disabled.contains(type.name()));
        }
        return result;
    }

    @Transactional
    public Map<String, Boolean> updateSetting(String typeName, boolean enabled) {
        NotificationType type = parseUserType(typeName);
        mapper.upsertSetting(AuthUtils.currentUserId(), type.name(), enabled);
        return mySettings();
    }

    public boolean isMutedByMe(String targetType, Long targetId) {
        requireMuteTarget(targetType, targetId);
        return mapper.existsMute(AuthUtils.currentUserId(), targetType, targetId);
    }

    @Transactional
    public boolean setMute(String targetType, Long targetId, boolean muted) {
        requireMuteTarget(targetType, targetId);
        Long userId = AuthUtils.currentUserId();
        if (muted) mapper.insertMute(userId, targetType, targetId);
        else mapper.deleteMute(userId, targetType, targetId);
        return muted;
    }

    private static NotificationType parseUserType(String typeName) {
        try {
            NotificationType type = NotificationType.valueOf(String.valueOf(typeName).toUpperCase());
            if (USER_TYPES.contains(type)) return type;
        } catch (IllegalArgumentException ignored) {
            // 아래 공통 예외로
        }
        throw new BaseException("고를 수 없는 알림 유형입니다: " + typeName, HttpStatus.BAD_REQUEST);
    }

    private static void requireMuteTarget(String targetType, Long targetId) {
        if (targetType == null || !MUTE_TARGETS.contains(targetType) || targetId == null) {
            throw new BaseException("targetType 은 post 또는 comment, targetId 는 필수입니다.", HttpStatus.BAD_REQUEST);
        }
    }
}
