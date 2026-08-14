package com.back.admin.post.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 선택 삭제(일괄). 사유는 선택된 게시글 전체에 동일 적용.
@Getter
@Setter
public class BulkPostDeleteRequest {
    private List<Long> postIds;
    private Long reasonId;
    private String detail;
}
