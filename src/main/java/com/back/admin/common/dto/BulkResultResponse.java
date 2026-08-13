package com.back.admin.common.dto;

import java.util.List;
import lombok.Getter;

// 일괄 처리 결과 (부분 성공). 성공 개수 + 실패 항목(id, 사유) 목록.
@Getter
public class BulkResultResponse {
    private final int successCount;
    private final int failCount;
    private final List<FailureItem> failures;

    public BulkResultResponse(int successCount, List<FailureItem> failures) {
        this.successCount = successCount;
        this.failCount = failures.size();
        this.failures = failures;
    }

    @Getter
    public static class FailureItem {
        private final Long id;      // 실패한 대상 id (postId / commentId)
        private final String message;

        public FailureItem(Long id, String message) {
            this.id = id;
            this.message = message;
        }
    }
}
