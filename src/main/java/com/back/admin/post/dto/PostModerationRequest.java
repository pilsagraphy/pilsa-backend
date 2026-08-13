package com.back.admin.post.dto;

import lombok.Getter;
import lombok.Setter;

// 블라인드/삭제 시 모달에서 선택한 사유. 복원(공개)에는 사용하지 않음.
@Getter
@Setter
public class PostModerationRequest {
    private Long reasonId;   // reasons.reason_id
    private String detail;   // ETC(기타) 선택 시 상세 사유, 그 외 null
}
