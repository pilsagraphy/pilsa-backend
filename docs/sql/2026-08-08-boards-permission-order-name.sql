-- ============================================================
--  게시판(boards) 테이블 수정
--  대상 DB : qa_pilsa (MySQL 8, InnoDB, utf8mb4_0900_ai_ci)
--  작성일  : 2026-08-08
--
--  1) 열람권한 / 작성권한 컬럼 추가
--  2) 게시판 정렬 순서 컬럼 추가
--  3) code 컬럼 -> name 으로 변경 + 영문명을 한글명으로 변경
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
-- 기대: 1=공지사항, 2=자유게시판, 3=정보게시판 (code 는 영문)


-- ------------------------------------------------------------
-- 1) 열람권한 / 작성권한 컬럼 추가
--
--    users 테이블의 role(관리레벨) 과 status(재학현황) 를 게시판 접근용
--    단일 등급 척도로 합쳐서 가져온다. 값이 클수록 좁은 권한이며,
--    "board.read_level 이하의 등급을 가진 회원은 열람 불가" 로 판정한다.
--
--      0 = 전체 공개 (비로그인 포함)
--      1 = 로그인 회원 전체 (users.role = ALUMNI / STUDENTS / ADMIN)
--      2 = 재학생 이상     (users.role = STUDENTS / ADMIN)
--      3 = 관리자 전용     (users.role = ADMIN)
--
--    users.status(0 재학 / 1 휴학 / 2 졸업)는 등급 자체로 쓰지 않는다.
--    졸업 처리된 회원은 role 이 ALUMNI 로 바뀌는 구조이므로 role 하나로 판정된다.
-- ------------------------------------------------------------
ALTER TABLE `boards`
  ADD COLUMN `read_level`  tinyint NOT NULL DEFAULT 1
    COMMENT '열람 권한 등급 (0:전체 1:회원 2:재학생 3:관리자)' AFTER `code`,
  ADD COLUMN `write_level` tinyint NOT NULL DEFAULT 1
    COMMENT '작성 권한 등급 (0:전체 1:회원 2:재학생 3:관리자)' AFTER `read_level`;

-- 현재 SecurityConfig 의 실제 동작과 동일하게 초기값을 맞춘다.
--   /api/stu/**   -> STUDENTS, ADMIN, ALUMNI  (로그인 회원 전체)
--   /api/admin/** -> ADMIN
-- 즉 이 마이그레이션만으로는 기존 동작이 바뀌지 않는다.
UPDATE `boards`
SET `read_level`  = 1,                                  -- 세 게시판 모두 로그인 회원이면 열람 가능
    `write_level` = CASE `board_id`
                      WHEN 1 THEN 3                     -- 공지사항: 관리자만 작성
                      ELSE 1                            -- 자유/정보: 로그인 회원 작성 가능
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
SELECT board_id, name, read_level, write_level, display_order,
       allow_comment, allow_attachment, category_mode
FROM boards
ORDER BY display_order;
-- 기대 결과
--  board_id | name       | read_level | write_level | display_order
--  ---------+------------+------------+-------------+--------------
--     1     | 공지사항    |     1      |      3      |      1
--     2     | 자유게시판  |     1      |      1      |      2
--     3     | 정보게시판  |     1      |      1      |      3


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
-- ALTER TABLE `boards` DROP COLUMN `read_level`;
--
-- ※ 롤백의 code 복원값(NOTICE/FREE/INFO)은 적용 전 [사전 확인] 쿼리 결과로
--    실제 값을 확인한 뒤 맞춰서 실행할 것.
