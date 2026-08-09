-- ============================================================
--  게시판(boards) 테이블 수정  [v2 — 권한 모델 재작성]
--  대상 DB : qa_pilsa (MySQL 8, InnoDB, utf8mb4_0900_ai_ci)
--  작성일  : 2026-08-08
--
--  1) 열람권한 / 작성권한 컬럼 추가
--  2) 게시판 정렬 순서 컬럼 추가
--  3) code 컬럼 -> name 으로 변경 + 영문명을 한글명으로 변경
--
--  ※ v1(단일 등급 0~3 척도)에서 바뀐 점
--     열람과 작성이 서로 다른 기준을 쓰므로 한 축으로 합칠 수 없다.
--       - 열람권한 : 재학현황(STUDENTS / ALUMNI) 기준  -> read_scope
--       - 작성권한 : 관리레벨(일반회원 / ADMIN 1·2·3) 기준 -> write_level
--     재학생과 졸업생은 상하관계가 아니라 서로 다른 집단이므로,
--     열람권한은 "등급 숫자"가 아니라 "대상 집합"으로 표현한다.
--
--  ※ 위에서 아래로 순서대로 실행할 것. 3번은 컬럼명 변경 후 값을 갱신하므로
--     중간에 끊으면 name 이 영문인 상태로 남는다.
--  ※ 이 프로젝트에는 Flyway/Liquibase 가 없어 수동 적용한다.
-- ============================================================


-- ------------------------------------------------------------
-- [사전 확인] 적용 전 현재 상태
-- ------------------------------------------------------------
SELECT board_id, code, allow_comment, allow_attachment, category_mode
FROM boards
ORDER BY board_id;
-- 기대: 1=NOTICE, 2=FREE, 3=INFO (code 는 영문)

-- v1 을 이미 적용했다면 아래 두 줄로 먼저 되돌린 뒤 이 파일을 실행한다.
-- ALTER TABLE `boards` DROP COLUMN `write_level`, DROP COLUMN `read_level`;
-- (display_order / name 은 v2 에서도 동일하므로 그대로 두면 된다)


-- ------------------------------------------------------------
-- [전제] users 테이블에 추가될 두 컬럼
--
--   이 파일의 권한 컬럼은 아래 두 값을 기준으로 판정한다.
--   users 쪽 작업자가 이미 추가했다면 이 블록은 건너뛴다.
--
--     재학현황 enrollment_status : STUDENTS(재학생) / ALUMNI(졸업생)
--     관리레벨 admin_level       : 0(일반회원) / 1 · 2 · 3(관리자)
--
--   기존 role 컬럼(ADMIN/STUDENTS/ALUMNI)은 JWT 클레임과 SecurityConfig 가
--   그대로 사용하므로 남겨둔다. 두 컬럼은 role 에서 백필한다.
-- ------------------------------------------------------------
-- ALTER TABLE `users`
--   ADD COLUMN `enrollment_status` varchar(20) NOT NULL DEFAULT 'STUDENTS'
--     COMMENT '재학현황: STUDENTS(재학생) / ALUMNI(졸업생)' AFTER `status`,
--   ADD COLUMN `admin_level` tinyint NOT NULL DEFAULT 0
--     COMMENT '관리레벨: 0(일반회원) / 1·2·3(관리자, 클수록 상위)' AFTER `enrollment_status`,
--   ADD KEY `idx_users_enrollment` (`enrollment_status`),
--   ADD KEY `idx_users_admin_level` (`admin_level`);
--
-- UPDATE `users`
-- SET `enrollment_status` = CASE WHEN `role` = 'ALUMNI' THEN 'ALUMNI' ELSE 'STUDENTS' END,
--     `admin_level`       = CASE WHEN `role` = 'ADMIN'  THEN 1 ELSE 0 END;
--
--   휴학(status = 1)은 재학생으로 본다. 졸업(status = 2)만 ALUMNI 로 분리된다.


-- ------------------------------------------------------------
-- 1) 열람권한 / 작성권한 컬럼 추가
--
--   [열람권한] read_scope — 재학현황 기준. 등급이 아니라 대상 집합이다.
--     'ALL'      : 비로그인 포함 전체 공개
--     'MEMBER'   : 로그인 회원 전체 (재학생 + 졸업생)
--     'STUDENTS' : 재학생 전용
--     'ALUMNI'   : 졸업생(동문) 전용
--     ※ 관리자(admin_level > 0)는 read_scope 와 무관하게 항상 열람 가능하다.
--
--   [작성권한] write_level — 관리레벨 기준. 이쪽은 상하관계가 명확하므로 숫자 등급.
--     0 : 일반회원 이상 (로그인 회원 누구나)
--     1 : 관리자 레벨 1 이상
--     2 : 관리자 레벨 2 이상
--     3 : 관리자 레벨 3 (최상위)
--     판정식: users.admin_level >= boards.write_level
--     ※ 숫자가 클수록 상위 권한으로 정의했다. 레벨 1이 최상위라면 이 정의와
--        아래 초기값을 반대로 뒤집어야 한다.
-- ------------------------------------------------------------
ALTER TABLE `boards`
  ADD COLUMN `read_scope` varchar(20) NOT NULL DEFAULT 'MEMBER'
    COMMENT '열람 대상: ALL(전체) / MEMBER(재학+졸업) / STUDENTS(재학생만) / ALUMNI(졸업생만)' AFTER `code`,
  ADD COLUMN `write_level` tinyint NOT NULL DEFAULT 0
    COMMENT '작성 최소 관리레벨: 0(일반회원) / 1·2·3(관리자)' AFTER `read_scope`;

-- 현재 SecurityConfig 의 실제 동작과 동일하게 초기값을 맞춘다.
--   /api/stu/**   -> STUDENTS, ADMIN, ALUMNI (로그인 회원 전체)  => read_scope = 'MEMBER'
--   공지 작성     -> ROLE_ADMIN 확인          => write_level = 1 (모든 관리자)
-- 즉 이 마이그레이션만으로는 기존 동작이 바뀌지 않는다.
UPDATE `boards`
SET `read_scope`  = 'MEMBER',                           -- 세 게시판 모두 로그인 회원이면 열람 가능
    `write_level` = CASE `board_id`
                      WHEN 1 THEN 1                     -- 공지사항: 관리자만 작성 (레벨 1 이상)
                      ELSE 0                            -- 자유/정보: 일반회원 작성 가능
                    END
WHERE `board_id` IN (1, 2, 3);


-- ------------------------------------------------------------
-- 2) 게시판 정렬 순서 컬럼 추가
--    categories.display_order 와 같은 이름/타입/기본값을 사용해 컨벤션을 맞춘다.
-- ------------------------------------------------------------
ALTER TABLE `boards`
  ADD COLUMN `display_order` int NOT NULL DEFAULT 0
    COMMENT '게시판 목록 노출 순서 (작을수록 먼저)' AFTER `write_level`,
  ADD KEY `idx_boards_display_order` (`display_order`);

UPDATE `boards`
SET `display_order` = CASE `board_id`
                        WHEN 1 THEN 1                   -- 공지사항
                        WHEN 2 THEN 2                   -- 자유게시판
                        WHEN 3 THEN 3                   -- 정보게시판
                      END
WHERE `board_id` IN (1, 2, 3);


-- ------------------------------------------------------------
-- 3) code -> name 변경 + 한글명으로 변경
--
--    boards 테이블은 애플리케이션 코드에서 조회되지 않고(게시판은
--    FREE_BOARD_ID = 2L 같은 상수로만 다룬다), code 값을 참조하는
--    매퍼도 없다. 따라서 컬럼명 변경으로 깨지는 코드가 없다.
--
--    varchar(20) -> varchar(50) 으로 넓힌다. categories.name 과 동일하게 맞추고,
--    한글 게시판명이 길어질 여지를 둔다.
-- ------------------------------------------------------------
ALTER TABLE `boards`
  CHANGE COLUMN `code` `name` varchar(50) NOT NULL COMMENT '게시판 이름 (화면 노출용)';

-- 컬럼명이 바뀌었으므로 유니크 키 이름도 함께 맞춘다.
ALTER TABLE `boards`
  RENAME INDEX `uq_boards_code` TO `uq_boards_name`;

-- 영문 식별자를 화면에 그대로 쓸 한글명으로 바꾼다.
-- code 값에 의존하지 않고 board_id 로 매칭한다. (코드 상수와 동일한 기준)
UPDATE `boards`
SET `name` = CASE `board_id`
               WHEN 1 THEN '공지사항'
               WHEN 2 THEN '자유게시판'
               WHEN 3 THEN '정보게시판'
             END
WHERE `board_id` IN (1, 2, 3);


-- ------------------------------------------------------------
-- [사후 확인] 적용 결과
-- ------------------------------------------------------------
SELECT board_id, name, read_scope, write_level, display_order,
       allow_comment, allow_attachment, category_mode
FROM boards
ORDER BY display_order;
-- 기대 결과
--  board_id | name       | read_scope | write_level | display_order
--  ---------+------------+------------+-------------+--------------
--     1     | 공지사항    |   MEMBER   |      1      |      1
--     2     | 자유게시판  |   MEMBER   |      0      |      2
--     3     | 정보게시판  |   MEMBER   |      0      |      3


-- ------------------------------------------------------------
-- [참고] 애플리케이션에서 쓸 판정 쿼리
--        (권한 판정 로직 구현 시 이 형태로 사용한다)
-- ------------------------------------------------------------
-- 내가 열람할 수 있는 게시판 목록
-- SELECT b.board_id, b.name
-- FROM boards b
-- JOIN users u ON u.user_id = #{userId}
-- WHERE b.read_scope = 'ALL'
--    OR b.read_scope = 'MEMBER'
--    OR b.read_scope = u.enrollment_status   -- STUDENTS / ALUMNI 정확히 일치
--    OR u.admin_level > 0                    -- 관리자는 전부 열람
-- ORDER BY b.display_order;
--
-- 비로그인 방문자
-- SELECT b.board_id, b.name FROM boards b WHERE b.read_scope = 'ALL' ORDER BY b.display_order;
--
-- 이 게시판에 글을 쓸 수 있는가
-- SELECT (u.admin_level >= b.write_level) AS can_write
-- FROM boards b JOIN users u ON u.user_id = #{userId}
-- WHERE b.board_id = #{boardId};


-- ============================================================
--  롤백 (적용 후 되돌려야 할 때)
-- ============================================================
-- UPDATE `boards`
-- SET `name` = CASE `board_id`
--                WHEN 1 THEN 'NOTICE'
--                WHEN 2 THEN 'FREE'
--                WHEN 3 THEN 'INFO'
--              END
-- WHERE `board_id` IN (1, 2, 3);
--
-- ALTER TABLE `boards` RENAME INDEX `uq_boards_name` TO `uq_boards_code`;
-- ALTER TABLE `boards`
--   CHANGE COLUMN `name` `code` varchar(20) NOT NULL COMMENT '게시판 영문 식별자';
--
-- ALTER TABLE `boards` DROP KEY `idx_boards_display_order`;
-- ALTER TABLE `boards` DROP COLUMN `display_order`;
-- ALTER TABLE `boards` DROP COLUMN `write_level`;
-- ALTER TABLE `boards` DROP COLUMN `read_scope`;
--
-- ※ 롤백의 code 복원값(NOTICE/FREE/INFO)은 적용 전 [사전 확인] 쿼리 결과로
--    실제 값을 확인한 뒤 맞춰서 실행할 것.
