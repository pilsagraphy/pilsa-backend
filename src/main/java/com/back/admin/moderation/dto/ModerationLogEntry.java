package com.back.admin.moderation.dto;

import lombok.Getter;
import lombok.Setter;

// moderation_log INSERT 파라미터 겸, 생성된 action_id 를 돌려받는 용도(useGeneratedKeys)
@Getter
@Setter
public class ModerationLogEntry {
    private Long actionId;       // INSERT 후 채워짐 (keyProperty)
    private String targetType;   // 'post' / 'comment'
    private Long targetId;
    private String appliedState; // normal / blind / deleted
    private Long reasonId;       // 복원(normal)은 null
    private String detail;       // ETC 상세 (없으면 null)
    private Long actedBy;        // 조치 관리자 user_id (null = 시스템)

    public ModerationLogEntry(String targetType, Long targetId, String appliedState,
                              Long reasonId, String detail, Long actedBy) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.appliedState = appliedState;
        this.reasonId = reasonId;
        this.detail = detail;
        this.actedBy = actedBy;
    }
}
