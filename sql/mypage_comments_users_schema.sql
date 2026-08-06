-- ============================================================
--  마이페이지 스키마 수정 — qa_pilsa
--
--  1차 초안 검토 결과 (PM 반영)
--   [수용] comments.parent_comment_id — 대댓글(1단)
--   [수용] users.student_no UNIQUE
--   [반려] comments.is_deleted              → 기존 state 컬럼 사용
--   [반려] users.suspended_until / is_banned → 기존 ban_status / banned_until 사용
--
--  ⚠️ 아직 qa_pilsa 에 미적용 — 팀 확인 후 실행 예정
--     (운영 pilsa 직접 적용 금지)
-- ============================================================

USE qa_pilsa;

-- [수정] comments — parent_comment_id 추가
ALTER TABLE `comments`
    ADD COLUMN `parent_comment_id` bigint NULL
        COMMENT '부모 댓글 ID (NULL=일반 댓글, 값 있으면 그 댓글의 답글)'
        AFTER `comment_id`;

ALTER TABLE `comments`
    ADD CONSTRAINT `fk_comments_parent`
        FOREIGN KEY (`parent_comment_id`) REFERENCES `comments` (`comment_id`)
            ON UPDATE CASCADE;

CREATE INDEX `idx_comments_parent` ON `comments` (`parent_comment_id`);

-- 대댓글 1단 제한은 FK로 강제 불가 → 앱 레벨 검증.
--   답글 저장 전, 대상 댓글이 '일반 댓글'인지 확인:
--   SELECT parent_comment_id FROM comments WHERE comment_id = #{targetCommentId} AND state = ... ;
--   결과 NULL → 답글 허용 / 값 있음(이미 답글) → 거부
-- ※ 삭제/블라인드 판정은 is_deleted 대신 기존 comments.state 사용 (값 규칙 담당자 확인)

-- [수정] users — student_no UNIQUE 추가 (선행: 중복 학번 없어야 함)
ALTER TABLE `users`
    ADD CONSTRAINT `uq_users_student_no` UNIQUE (`student_no`);

-- ※ 정지/차단은 신규 컬럼 대신 기존 users.ban_status / users.banned_until 사용 (인증 담당 협의)

-- [참고] users.role 2축 분리 검토 (미확정 · 담당자 확인)
--   as-is : role       권한(ADMIN / STUDENTS / ALUMNI)
--   to-be : 재학현황    STUDENTS / ALUMNI
--           관리레벨    ADMIN(레벨1·2·3) / 일반회원
