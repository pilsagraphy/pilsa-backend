-- ============================================================
-- 제재회원관리 화면 테스트용 시나리오 픽스처
-- 실행 전제: sanction_seed.sql, reports_log_schema_update.sql 를 먼저 실행할 것
-- 실행 방법: 전체를 한 세션(스크립트 실행)으로 실행해야 함 (LAST_INSERT_ID() 세션변수 사용)
-- 테스트 계정 공용 비밀번호: Test1234!  (BCrypt: $2a$10$aVbi97czhxp8gtp6Buzi8eXSuwIVmIxFB24Du.cfYygGalJiD6Etm)
-- 정리(삭제)하려면 파일 맨 아래 CLEANUP 섹션 참고
-- ============================================================

SET @PW_HASH = '$2a$10$aVbi97czhxp8gtp6Buzi8eXSuwIVmIxFB24Du.cfYygGalJiD6Etm';
SET @FREE_BOARD_ID = 2;

-- ============================================================
-- 1) test_caution : 주의 태그(캐션 8점, 아직 정지/차단 아님)
--    -> Swagger에서 아래 '라이브 삭제 테스트용 게시글'을 관리자 강제삭제 API로 삭제하면
--       8+2=10점이 되어 그 순간 경고 1회 + 1주 정지로 전환되는 걸 실시간으로 확인 가능
-- ============================================================
INSERT INTO users (name, phone, major, student_no, email, login_id, password_hash, role, is_deleted)
VALUES ('테스트_주의', '010-9999-0001', 'TEST', '90000001', 'test_caution@qa.pilsa.test', 'test_caution', @PW_HASH, 'STUDENTS', 0);
SET @caution_user_id = LAST_INSERT_ID();

INSERT INTO posts (title, content, user_id, board_id, is_anonymous, created_at, state)
VALUES ('[TEST] 주의 히스토리용 게시글', '시드 데이터 - 이미 처리된 것으로 간주', @caution_user_id, @FREE_BOARD_ID, 0, NOW(), 'deleted');
SET @caution_post_hist = LAST_INSERT_ID();

INSERT INTO moderation_log (target_type, target_id, applied_state, reason_id, detail, acted_by, created_at)
VALUES ('post', @caution_post_hist, 'deleted', (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1), '[TEST] 시드 데이터', NULL, NOW());
SET @caution_action_id = LAST_INSERT_ID();

INSERT INTO penalty_log (user_id, points, target_type, target_id, source_action_id, created_at, expires_at)
VALUES (@caution_user_id, 8, 'post', @caution_post_hist, @caution_action_id, NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY));

INSERT INTO posts (title, content, user_id, board_id, is_anonymous, created_at, state)
VALUES ('[TEST] 라이브 삭제 테스트용 게시글', 'Swagger에서 관리자 강제삭제 API로 이 글을 삭제해보세요', @caution_user_id, @FREE_BOARD_ID, 0, NOW(), 'normal');
SET @caution_post_live = LAST_INSERT_ID();

-- ============================================================
-- 2) test_susp1w : 경고 1회 -> 1주 정지 중
-- ============================================================
INSERT INTO users (name, phone, major, student_no, email, login_id, password_hash, role, is_deleted)
VALUES ('테스트_정지1주', '010-9999-0002', 'TEST', '90000002', 'test_susp1w@qa.pilsa.test', 'test_susp1w', @PW_HASH, 'STUDENTS', 0);
SET @susp1w_id = LAST_INSERT_ID();

INSERT INTO posts (title, content, user_id, board_id, is_anonymous, created_at, state)
VALUES ('[TEST] 정지1주 히스토리용 게시글', '시드 데이터', @susp1w_id, @FREE_BOARD_ID, 0, NOW(), 'deleted');
SET @susp1w_post = LAST_INSERT_ID();

INSERT INTO moderation_log (target_type, target_id, applied_state, reason_id, detail, acted_by, created_at)
VALUES ('post', @susp1w_post, 'deleted', (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1), '[TEST] 시드 데이터', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY));
SET @susp1w_action = LAST_INSERT_ID();

INSERT INTO penalty_log (user_id, points, target_type, target_id, source_action_id, created_at, expires_at)
VALUES (@susp1w_id, 10, 'post', @susp1w_post, @susp1w_action, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 89 DAY));

INSERT INTO warning_log (user_id, created_at, expires_at)
VALUES (@susp1w_id, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 364 DAY));

INSERT INTO ban_log (user_id, warning_no, ban_type, starts_at, ends_at, created_at)
VALUES (@susp1w_id, 1, 'temporary', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

UPDATE users SET ban_status = 'temporary', banned_until = DATE_ADD(NOW(), INTERVAL 6 DAY) WHERE user_id = @susp1w_id;

-- ============================================================
-- 3) test_susp1m : 경고 2회 -> 1개월 정지 중
-- ============================================================
INSERT INTO users (name, phone, major, student_no, email, login_id, password_hash, role, is_deleted)
VALUES ('테스트_정지1개월', '010-9999-0003', 'TEST', '90000003', 'test_susp1m@qa.pilsa.test', 'test_susp1m', @PW_HASH, 'STUDENTS', 0);
SET @susp1m_id = LAST_INSERT_ID();

INSERT INTO posts (title, content, user_id, board_id, is_anonymous, created_at, state)
VALUES ('[TEST] 정지1개월 히스토리용 게시글', '시드 데이터', @susp1m_id, @FREE_BOARD_ID, 0, NOW(), 'deleted');
SET @susp1m_post = LAST_INSERT_ID();

INSERT INTO moderation_log (target_type, target_id, applied_state, reason_id, detail, acted_by, created_at)
VALUES ('post', @susp1m_post, 'deleted', (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1), '[TEST] 시드 데이터', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY));
SET @susp1m_action = LAST_INSERT_ID();

INSERT INTO penalty_log (user_id, points, target_type, target_id, source_action_id, created_at, expires_at)
VALUES (@susp1m_id, 20, 'post', @susp1m_post, @susp1m_action, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 88 DAY));

INSERT INTO warning_log (user_id, created_at, expires_at) VALUES
    (@susp1m_id, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 363 DAY)),
    (@susp1m_id, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 363 DAY));

INSERT INTO ban_log (user_id, warning_no, ban_type, starts_at, ends_at, created_at)
VALUES (@susp1m_id, 2, 'temporary', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 28 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

UPDATE users SET ban_status = 'temporary', banned_until = DATE_ADD(NOW(), INTERVAL 28 DAY) WHERE user_id = @susp1m_id;

-- ============================================================
-- 4) test_banned : 경고 3회 -> 영구차단
-- ============================================================
INSERT INTO users (name, phone, major, student_no, email, login_id, password_hash, role, is_deleted)
VALUES ('테스트_영구차단', '010-9999-0004', 'TEST', '90000004', 'test_banned@qa.pilsa.test', 'test_banned', @PW_HASH, 'STUDENTS', 0);
SET @banned_id = LAST_INSERT_ID();

INSERT INTO posts (title, content, user_id, board_id, is_anonymous, created_at, state)
VALUES ('[TEST] 영구차단 히스토리용 게시글', '시드 데이터', @banned_id, @FREE_BOARD_ID, 0, NOW(), 'deleted');
SET @banned_post = LAST_INSERT_ID();

INSERT INTO moderation_log (target_type, target_id, applied_state, reason_id, detail, acted_by, created_at)
VALUES ('post', @banned_post, 'deleted', (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1), '[TEST] 시드 데이터', NULL, DATE_SUB(NOW(), INTERVAL 3 DAY));
SET @banned_action = LAST_INSERT_ID();

INSERT INTO penalty_log (user_id, points, target_type, target_id, source_action_id, created_at, expires_at)
VALUES (@banned_id, 30, 'post', @banned_post, @banned_action, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 87 DAY));

INSERT INTO warning_log (user_id, created_at, expires_at) VALUES
    (@banned_id, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 362 DAY)),
    (@banned_id, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 362 DAY)),
    (@banned_id, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 362 DAY));

INSERT INTO ban_log (user_id, warning_no, ban_type, starts_at, ends_at, created_at)
VALUES (@banned_id, 3, 'permanent', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 3 DAY));

UPDATE users SET ban_status = 'permanent', banned_until = NULL WHERE user_id = @banned_id;

-- ============================================================
-- 5) test_expired : 정지 기간은 이미 지났지만 ban_status 캐시가 아직 안 정리된 상태
--    -> 로그인은 즉시 성공해야 함 (실시간 판정), 목록엔 스케줄러 돌기 전까지 계속 '정지'로 남아있어야 함
-- ============================================================
INSERT INTO users (name, phone, major, student_no, email, login_id, password_hash, role, is_deleted)
VALUES ('테스트_만료됨', '010-9999-0005', 'TEST', '90000005', 'test_expired@qa.pilsa.test', 'test_expired', @PW_HASH, 'STUDENTS', 0);
SET @expired_id = LAST_INSERT_ID();

INSERT INTO posts (title, content, user_id, board_id, is_anonymous, created_at, state)
VALUES ('[TEST] 정지만료 히스토리용 게시글', '시드 데이터', @expired_id, @FREE_BOARD_ID, 0, NOW(), 'deleted');
SET @expired_post = LAST_INSERT_ID();

INSERT INTO moderation_log (target_type, target_id, applied_state, reason_id, detail, acted_by, created_at)
VALUES ('post', @expired_post, 'deleted', (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1), '[TEST] 시드 데이터', NULL, DATE_SUB(NOW(), INTERVAL 8 DAY));
SET @expired_action = LAST_INSERT_ID();

INSERT INTO penalty_log (user_id, points, target_type, target_id, source_action_id, created_at, expires_at)
VALUES (@expired_id, 10, 'post', @expired_post, @expired_action, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 82 DAY));

INSERT INTO warning_log (user_id, created_at, expires_at)
VALUES (@expired_id, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 357 DAY));

INSERT INTO ban_log (user_id, warning_no, ban_type, starts_at, ends_at, created_at)
VALUES (@expired_id, 1, 'temporary', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY));

-- 스케줄러가 아직 안 돈 것처럼 일부러 캐시를 그대로(temporary) 남겨둠
UPDATE users SET ban_status = 'temporary', banned_until = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE user_id = @expired_id;

-- ============================================================
-- 6) 신고 시나리오: test_reported_author(피신고자) / test_reporter(신고자)
-- ============================================================
INSERT INTO users (name, phone, major, student_no, email, login_id, password_hash, role, is_deleted)
VALUES ('테스트_피신고자', '010-9999-0006', 'TEST', '90000006', 'test_reported@qa.pilsa.test', 'test_reported_author', @PW_HASH, 'STUDENTS', 0);
SET @reported_id = LAST_INSERT_ID();

INSERT INTO users (name, phone, major, student_no, email, login_id, password_hash, role, is_deleted)
VALUES ('테스트_신고자', '010-9999-0007', 'TEST', '90000007', 'test_reporter@qa.pilsa.test', 'test_reporter', @PW_HASH, 'STUDENTS', 0);
SET @reporter_id = LAST_INSERT_ID();

-- 6-1) 아직 처리 안 된(pending) 신고 대상 게시글 + 댓글 -> Swagger로 수락/거절 라이브 테스트용
INSERT INTO posts (title, content, user_id, board_id, is_anonymous, created_at, state)
VALUES ('[TEST] 신고 대기중인 게시글', 'Swagger에서 신고 수락/거절 API로 처리해보세요', @reported_id, @FREE_BOARD_ID, 0, NOW(), 'normal');
SET @rep_post_pending = LAST_INSERT_ID();

INSERT INTO comments (post_id, user_id, content, is_anonymous, created_at, state)
VALUES (@rep_post_pending, @reported_id, '[TEST] 신고 대기중인 댓글', 0, NOW(), 'normal');
SET @rep_comment_pending = LAST_INSERT_ID();

INSERT INTO reports_log (reporter_id, target_type, target_id, reason_id, detail, status, created_at)
VALUES (@reporter_id, 'post', @rep_post_pending, (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1), NULL, 'pending', NOW());

INSERT INTO reports_log (reporter_id, target_type, target_id, reason_id, detail, status, created_at)
VALUES (@reporter_id, 'comment', @rep_comment_pending,
        COALESCE((SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1 OFFSET 1),
                 (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1)),
        NULL, 'pending', NOW());

-- 6-2) 이미 수락(삭제 처리)된 신고 히스토리
INSERT INTO posts (title, content, user_id, board_id, is_anonymous, created_at, state)
VALUES ('[TEST] 이미 삭제 처리된 게시글', '시드 데이터', @reported_id, @FREE_BOARD_ID, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), 'deleted');
SET @rep_post_resolved = LAST_INSERT_ID();

INSERT INTO moderation_log (target_type, target_id, applied_state, reason_id, detail, acted_by, created_at)
VALUES ('post', @rep_post_resolved, 'deleted', (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1), '[TEST] 시드 데이터', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY));
SET @rep_action_resolved = LAST_INSERT_ID();

INSERT INTO penalty_log (user_id, points, target_type, target_id, source_action_id, created_at, expires_at)
VALUES (@reported_id, 2, 'post', @rep_post_resolved, @rep_action_resolved, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 89 DAY));

INSERT INTO reports_log (reporter_id, target_type, target_id, reason_id, detail, status, created_at, resolved_at, resolution_action_id)
VALUES (@reporter_id, 'post', @rep_post_resolved, (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1), NULL, 'resolved', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), @rep_action_resolved);

-- 6-3) 거절된 신고 히스토리
INSERT INTO posts (title, content, user_id, board_id, is_anonymous, created_at, state)
VALUES ('[TEST] 신고가 거절된 게시글', '시드 데이터', @reported_id, @FREE_BOARD_ID, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), 'normal');
SET @rep_post_rejected = LAST_INSERT_ID();

INSERT INTO reports_log (reporter_id, target_type, target_id, reason_id, detail, status, created_at, resolved_at, resolution_action_id)
VALUES (@reporter_id, 'post', @rep_post_rejected,
        COALESCE((SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1 OFFSET 2),
                 (SELECT reason_id FROM reasons ORDER BY reason_id LIMIT 1)),
        '[TEST] 근거 부족으로 거절', 'rejected', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL);

-- ============================================================
-- CLEANUP (테스트 끝나고 지울 때 이 아래를 별도로 실행)
-- ============================================================
-- DELETE FROM reports_log WHERE reporter_id IN (SELECT user_id FROM users WHERE login_id LIKE 'test_%');
-- DELETE FROM ban_log WHERE user_id IN (SELECT user_id FROM users WHERE login_id LIKE 'test_%');
-- DELETE FROM warning_log WHERE user_id IN (SELECT user_id FROM users WHERE login_id LIKE 'test_%');
-- DELETE FROM penalty_log WHERE user_id IN (SELECT user_id FROM users WHERE login_id LIKE 'test_%');
-- DELETE FROM moderation_log WHERE acted_by IS NULL AND detail = '[TEST] 시드 데이터';
-- DELETE FROM comments WHERE user_id IN (SELECT user_id FROM users WHERE login_id LIKE 'test_%');
-- DELETE FROM posts WHERE user_id IN (SELECT user_id FROM users WHERE login_id LIKE 'test_%');
-- DELETE FROM users WHERE login_id LIKE 'test_%';
