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
        applyState(targetType, targetId, ModerationState.BLIND);
        writeLog(targetType, targetId, ModerationState.BLIND, reasonId, detail, actorId);
    }

    @Override
    public void restore(String targetType, Long targetId, Long actorId) {
        applyState(targetType, targetId, ModerationState.NORMAL);
        // 복원은 사유 없음(reasonId=null). 조치 이력을 남기고 그 action_id 로 주의 포인트를 회수한다.
        ModerationLogEntry entry = writeLog(targetType, targetId, ModerationState.NORMAL, null, null, actorId);
        moderationMapper.voidPenaltiesByTarget(targetType, targetId, entry.getActionId());
    }

    @Override
    public void softDelete(String targetType, Long targetId, Long actorId, Long reasonId, String detail) {
        applyState(targetType, targetId, ModerationState.DELETED);
        ModerationLogEntry entry = writeLog(targetType, targetId, ModerationState.DELETED, reasonId, detail, actorId);

        Long authorId = findAuthorId(targetType, targetId);
        if (authorId == null) {
            throw new ModerationException("작성자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        // 작성자에게 주의 포인트 적립 (+2, 시효는 policy_settings 기준)
        moderationMapper.insertPenalty(authorId, targetType, targetId, entry.getActionId());
    }

    // ---- 내부 헬퍼 ----

    // targetType 에 맞는 테이블의 state 를 변경. 대상이 없으면 예외.
    private void applyState(String targetType, Long targetId, ModerationState state) {
        int updated;
        if (TARGET_POST.equals(targetType)) {
            updated = moderationMapper.updatePostState(targetId, state.dbValue());
        } else if (TARGET_COMMENT.equals(targetType)) {
            updated = moderationMapper.updateCommentState(targetId, state.dbValue());
        } else {
            throw new ModerationException("잘못된 대상 유형입니다: " + targetType, HttpStatus.BAD_REQUEST);
        }
        if (updated == 0) {
            throw new ModerationException("대상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
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
