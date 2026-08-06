package com.back.admin.moderation.service;

import com.back.admin.moderation.dto.ModerationLogEntry;
import com.back.admin.moderation.dto.ModerationState;
import com.back.admin.moderation.exception.ModerationException;
import com.back.admin.moderation.mapper.ModerationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ModerationServiceImpl implements ModerationService {

    public static final String TARGET_POST = "post";
    public static final String TARGET_COMMENT = "comment";

    private final ModerationMapper moderationMapper;

    @Override
    public void blind(String targetType, Long targetId, Long actorId, Long reasonId, String detail) {
        if (!changeState(targetType, targetId, ModerationState.BLIND)) return; // 이미 blind면 no-op
        writeLog(targetType, targetId, ModerationState.BLIND, reasonId, detail, actorId);
    }

    @Override
    public void restore(String targetType, Long targetId, Long actorId) {
        if (!changeState(targetType, targetId, ModerationState.NORMAL)) return; // 이미 normal이면 no-op
        // 복원은 사유 없음(reasonId=null). 조치 이력을 남기고 그 action_id 로 주의 포인트를 회수한다.
        ModerationLogEntry entry = writeLog(targetType, targetId, ModerationState.NORMAL, null, null, actorId);
        moderationMapper.voidPenaltiesByTarget(targetType, targetId, entry.getActionId());
    }

    @Override
    public void softDelete(String targetType, Long targetId, Long actorId, Long reasonId, String detail) {
        if (!changeState(targetType, targetId, ModerationState.DELETED)) return; // 이미 deleted면 no-op (벌점 중복 방지)
        ModerationLogEntry entry = writeLog(targetType, targetId, ModerationState.DELETED, reasonId, detail, actorId);

        Long authorId = findAuthorId(targetType, targetId);
        if (authorId == null) {
            throw new ModerationException("작성자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        // 작성자에게 주의 포인트 적립 (+2, 시효는 policy_settings 기준)
        moderationMapper.insertPenalty(authorId, targetType, targetId, entry.getActionId());
    }

    @Override
    public String currentState(String targetType, Long targetId) {
        if (TARGET_POST.equals(targetType)) {
            return moderationMapper.findPostState(targetId);
        } else if (TARGET_COMMENT.equals(targetType)) {
            return moderationMapper.findCommentState(targetId);
        }
        throw new ModerationException("잘못된 대상 유형입니다: " + targetType, HttpStatus.BAD_REQUEST);
    }

    // ---- 내부 헬퍼 ----

    // 상태를 변경한다. 실제로 바뀌면 true, 이미 같은 상태면 false(no-op), 대상이 없으면 예외.
    // 조건부 UPDATE(state <> #{state})로 원자적 처리 → 동시 요청/재시도에도 중복 로그·벌점 방지.
    // (이미 그 상태인 행은 WHERE 에 걸리지 않아 0건 반환 → CLIENT_FOUND_ROWS 모드에서도 신뢰 가능)
    private boolean changeState(String targetType, Long targetId, ModerationState state) {
        int updated;
        if (TARGET_POST.equals(targetType)) {
            updated = moderationMapper.updatePostState(targetId, state.dbValue());
        } else if (TARGET_COMMENT.equals(targetType)) {
            updated = moderationMapper.updateCommentState(targetId, state.dbValue());
        } else {
            throw new ModerationException("잘못된 대상 유형입니다: " + targetType, HttpStatus.BAD_REQUEST);
        }
        if (updated > 0) {
            return true; // 실제로 상태가 바뀜
        }
        // 0건: 이미 목표 상태이거나 대상이 없음 → 존재 여부로 구분
        if (currentState(targetType, targetId) == null) {
            throw new ModerationException("대상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        return false; // 이미 목표 상태 → no-op
    }

    private ModerationLogEntry writeLog(String targetType, Long targetId, ModerationState state,
                                        Long reasonId, String detail, Long actorId) {
        ModerationLogEntry entry = new ModerationLogEntry(
                targetType, targetId, state.dbValue(), reasonId, detail, actorId);
        moderationMapper.insertModerationLog(entry);
        return entry;
    }

    private Long findAuthorId(String targetType, Long targetId) {
        if (TARGET_POST.equals(targetType)) {
            return moderationMapper.findPostAuthorId(targetId);
        } else if (TARGET_COMMENT.equals(targetType)) {
            return moderationMapper.findCommentAuthorId(targetId);
        }
        throw new ModerationException("잘못된 대상 유형입니다: " + targetType, HttpStatus.BAD_REQUEST);
    }
}
