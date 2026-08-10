-- =====================================================================
-- users 권한 체계 개편: role → member_type + admin_level  (+ status 제거)
-- 대상: qa_pilsa
-- ⚠️ 코드(PR #66)와 반드시 동시 배포. 한쪽만 반영하면 로그인/권한이 깨짐.
-- =====================================================================
USE qa_pilsa;

-- 1) 신분/관리등급 컬럼 추가
ALTER TABLE `users`
  ADD COLUMN `member_type` varchar(20) NOT NULL DEFAULT 'STUDENT'
    COMMENT '회원 구분(STUDENT: 재학생 / ALUMNI: 졸업생)' AFTER `status`,
  ADD COLUMN `admin_level` tinyint NOT NULL DEFAULT 0
    COMMENT '관리 권한 레벨(0: 일반회원 / 1~3: 관리자 등급)' AFTER `member_type`;

-- 2) 기존 role → 신규 컬럼으로 데이터 이관
UPDATE `users` SET `member_type` = 'ALUMNI'  WHERE `role` = 'ALUMNI';
UPDATE `users` SET `member_type` = 'STUDENT' WHERE `role` = 'STUDENTS';
UPDATE `users` SET `admin_level` = 3         WHERE `role` = 'ADMIN';

-- 3) 중복/미사용 컬럼 제거
ALTER TABLE `users` DROP COLUMN `role`;    -- member_type + admin_level 로 대체
ALTER TABLE `users` DROP COLUMN `status`;  -- member_type 와 의미 중복 → 제거
-- 참고: 기존 status(0:재학/1:휴학/2:졸업) 중 '휴학' 구분은 member_type(STUDENT/ALUMNI)에 없음.
--       휴학 상태가 필요하면 별도 컬럼 논의 필요.
