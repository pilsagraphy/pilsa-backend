package com.back.admin.moderation.mapper;

import com.back.admin.moderation.dto.ModerationLogEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ModerationMapper {

    // 게시글 표시 상태 변경 (normal/blind/deleted)
    int updatePostState(@Param("postId") Long postId, @Param("state") String state);

    // 댓글 표시 상태 변경
    int updateCommentState(@Param("commentId") Long commentId, @Param("state") String state);

    // 게시글 작성자 user_id 조회
    Long findPostAuthorId(@Param("postId") Long postId);

    // 댓글 작성자 user_id 조회
    Long findCommentAuthorId(@Param("commentId") Long commentId);

    // 조치 이력 기록. 성공 시 entry.actionId 에 생성된 PK 가 채워짐
    void insertModerationLog(@Param("entry") ModerationLogEntry entry);

    // 주의 포인트 적립 (points/시효는 policy_settings 값 사용, 없으면 기본 2/365)
    void insertPenalty(
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("sourceActionId") Long sourceActionId
    );

    // 대상(target)에 걸린 미회수 주의 포인트를 복원 조치로 회수(void)
    int voidPenaltiesByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("voidActionId") Long voidActionId
    );
}
