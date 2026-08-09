-- ============================================================
--  게시판(boards) 테이블 - 미사용 컬럼 제거
--  대상 DB : qa_pilsa (MySQL 8, InnoDB, utf8mb4_0900_ai_ci)
--  작성일  : 2026-08-09
--
--  대상 컬럼 : allow_comment, allow_attachment, category_mode
--
--  [조사 결과]
--  - 통합 게시판 코드(com.back.board, origin/게시판 브랜치)는 게시판별 정책을
--    boardId 기준 하드코딩된 BoardType enum(BoardType.java)으로 결정하며,
--    boards 테이블의 allow_comment / allow_attachment / category_mode 컬럼은
--    참조하지 않는다.
--  - 현재 dev / 일정관리 등 다른 브랜치에도 boards 테이블을 조회하는 코드가
--    없다 (공지/자유/정보 게시판은 com.back.student.notice/free/info 패키지가
--    FREE_BOARD_ID=2L 같은 상수로 board_id 를 직접 다루고, boards 테이블의
--    이 3개 컬럼을 읽거나 쓰는 매퍼/서비스 코드는 저장소 전체에서 발견되지
--    않았다).
--  - 즉 이 3개 컬럼은 어떤 기능에도 영향을 주지 않고 제거 가능하다.
--
--  ※ read_level / write_level / display_order 컬럼 추가, code -> name 변경은
--     별도 브랜치(관리자페이지-게시판관리-db수정, 양영환)에서 진행 중이며,
--     이 스크립트는 다른 컬럼만 다루므로 그 작업과 순서 무관하게 적용 가능하다.
--  ※ 이 프로젝트에는 Flyway/Liquibase 가 없어 수동 적용한다.
-- ============================================================


-- ------------------------------------------------------------
-- [사전 확인] 적용 전 현재 상태 (제거 전 값 기록용)
-- ------------------------------------------------------------
SELECT board_id, code, allow_comment, allow_attachment, category_mode
FROM boards
ORDER BY board_id;


-- ------------------------------------------------------------
-- 컬럼 제거
-- ------------------------------------------------------------
ALTER TABLE `boards`
  DROP COLUMN `allow_comment`,
  DROP COLUMN `allow_attachment`,
  DROP COLUMN `category_mode`;


-- ------------------------------------------------------------
-- [사후 확인] 적용 결과
-- ------------------------------------------------------------
SELECT *
FROM boards
ORDER BY board_id;


-- ============================================================
--  롤백 (적용 후 되돌려야 할 때)
--  ※ 컬럼 순서는 원래와 다를 수 있음(끝에 추가됨). 값은 위 [사전 확인]
--     결과를 보고 필요 시 UPDATE로 복원할 것 (아래는 컬럼 구조만 복원).
-- ============================================================
-- ALTER TABLE `boards`
--   ADD COLUMN `allow_comment` tinyint(1) NOT NULL DEFAULT 1,
--   ADD COLUMN `allow_attachment` tinyint(1) NOT NULL DEFAULT 1,
--   ADD COLUMN `category_mode` tinyint NOT NULL DEFAULT 0;
