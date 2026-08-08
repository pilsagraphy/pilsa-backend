-- 대댓글(답글) 기능용 마이그레이션
-- comments 테이블에 부모 댓글 참조 컬럼 추가 (무제한 깊이 지원, 평면 + parentId 방식)
-- 실행 위치: qa_pilsa DB (수동 실행 필요 - 프로젝트에 Flyway/Liquibase 없음)

-- 1) 부모 댓글 ID 컬럼 추가 (NULL = 최상위 댓글, 값 있으면 그 댓글의 답글)
ALTER TABLE comments
    ADD COLUMN parent_comment_id BIGINT NULL DEFAULT NULL AFTER post_id;

-- 2) 자기 참조 FK. 부모 댓글이 삭제되면 하위 답글도 함께 삭제(CASCADE)
ALTER TABLE comments
    ADD CONSTRAINT fk_comments_parent
        FOREIGN KEY (parent_comment_id) REFERENCES comments (comment_id)
            ON DELETE CASCADE;

-- 조회 성능용 인덱스 (특정 부모의 답글 조회 가속)
CREATE INDEX idx_comments_parent ON comments (parent_comment_id);
