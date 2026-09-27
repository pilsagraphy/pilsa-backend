package com.back.admin.sanction.dto;

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
        private final Long id;      // 실패한 대상 id (targetId = postId / commentId)
        private final String message;
        // 아래는 "댓글 복원 전 원 게시글부터 복원" 안내용 (code = POST_NOT_VISIBLE 일 때만 채움, 그 외 null).
        // 프론트가 원 게시글 신고 관리 · 작성자의 제재 회원 화면으로 바로 보낼 수 있게 필요한 값을 함께 준다 (2026-09-27)
        private final String code;
        private final Long postId;       // 원 게시글
        private final String postState;  // blind / deleted
        private final String deletedBy;  // author(작성자 직접) / admin(관리자 조치)
        private final Long authorId;     // 원 게시글 작성자
        private final String authorName;

        public FailureItem(Long id, String message) {
            this(id, message, null, null, null, null, null, null);
        }

        public FailureItem(Long id, String message, String code, Long postId, String postState,
                           String deletedBy, Long authorId, String authorName) {
            this.id = id;
            this.message = message;
            this.code = code;
            this.postId = postId;
            this.postState = postState;
            this.deletedBy = deletedBy;
            this.authorId = authorId;
            this.authorName = authorName;
        }
    }
}
