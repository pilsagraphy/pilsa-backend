package com.back.admin.post.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 글·댓글에 남은 마지막 조치. 관리자 상세에서 '왜 이 상태인가'를 한 줄로 보여 주기 위해.
 *  - 관리자가 한 조치: actorName 이 있다.
 *  - 신고 누적 자동 블라인드: acted_by 가 비어 isAuto 가 true 다.
 *  - 조치 기록이 없는데 삭제 상태면 작성자가 스스로 지운 것이다 (화면이 그렇게 읽는다).
 */
@Getter
@Setter
public class ModerationNoteResponse {
    private String targetType;      // post / comment
    private Long targetId;
    private String appliedState;    // blind / deleted / normal(복원)
    private String actorName;       // 조치한 관리자. 자동이면 null
    private Boolean isAuto;
    private String reasonLabel;
    private String detail;
    private LocalDateTime createdAt;
}
