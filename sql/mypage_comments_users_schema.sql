-- =====================================================================
-- 마이페이지 · 댓글/유저 테이블 수정안 (1차)
-- 대상 DB: qa_pilsa  (운영 pilsa 는 절대 직접 적용 금지)
--
-- ⚠️ 이 DDL 은 아직 qa_pilsa 에 미적용 상태입니다. 팀 확인 후 실행 예정.
--    - posts/comments 의 삭제·블라인드 컬럼 최종 확정(게시글 담당)
--    - 로그인 정지/차단(suspended_until, is_banned) 판단 로직(인증 담당)
--    협의 완료 후 아래를 순서대로 실행하세요.
-- =====================================================================
USE qa_pilsa;

-- ── comments: 대댓글(1단) 지원 ─────────────────────────────
ALTER TABLE comments
    ADD COLUMN parent_comment_id BIGINT NULL
        COMMENT '부모 댓글 ID (NULL=일반 댓글, 값 있으면 그 댓글의 답글)'
        AFTER comment_id;

-- [임시] 삭제 여부 placeholder — 게시글 담당 컬럼 확정 시 교체
ALTER TABLE comments
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '[임시] 삭제 여부 (0=정상, 1=삭제됨)';

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_parent
        FOREIGN KEY (parent_comment_id) REFERENCES comments (comment_id)
            ON UPDATE CASCADE ON DELETE CASCADE;

CREATE INDEX idx_comments_parent ON comments (parent_comment_id);

-- 대댓글 1단 제한은 FK 로 강제 불가 → 앱 레벨 검증.
-- 답글 저장 전, 대상 댓글이 '일반 댓글'인지 확인:
--   SELECT parent_comment_id FROM comments
--   WHERE comment_id = #{targetCommentId} AND is_deleted = 0;
--   결과 NULL → 답글 허용 / 값 있음(이미 답글) → 거부

-- ── users: 학번 유니크 + 정지/차단 ────────────────────────
ALTER TABLE users
    ADD CONSTRAINT uq_users_student_no UNIQUE (student_no);

ALTER TABLE users
    ADD COLUMN suspended_until DATETIME NULL
        COMMENT '정지 종료 일시 (NULL=정지 아님, 미래 값=정지 중)';

ALTER TABLE users
    ADD COLUMN is_banned TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '영구 차단 여부 (1=영구 차단)';
