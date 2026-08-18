# 20260814 통합 병합 체크리스트

> PM 검토용. PR #57/#60/#61/#62/#66/#68/#69/#70 (8건)을 `dev` 기준으로 검토·통합해
> `20260814` 브랜치에 최종 병합본을 만든다. 작성: Claude (PM psns0122 위임), 2026-08-14.

---

## 1. PR별 판정 요약

| PR | 제목 | 작성자 | 판정 | 비고 |
|----|------|--------|------|------|
| #66 | 권한 체계 개편 (role → member_type + admin_level) | doiob31 | ✅ 병합 (1순위) | DDL은 qa_pilsa에 **이미 적용됨** → dev 코드가 현재 DB와 불일치 상태라 최우선 병합 필수 |
| #62 | 재학생 게시판 board 패키지 통합 (+대댓글) | SARAyeon | ✅ 병합 (2순위) | free/info/notice 3패키지 삭제 → #57·#68의 student 변경분과 충돌. 대댓글 DDL은 DB 적용 완료 |
| #57 | 관리자 커뮤니티 관리 (게시글/신고/moderation) | olly3616 | ✅ 병합 (3순위) | admin 신규 패키지는 그대로. student 매퍼 수정분은 #62가 파일 삭제 → **BoardMapper로 포팅** |
| #68 | 제재 시스템 (주의→경고→정지/차단) + 신고 접수 | hams9494 | ⚠️ 병합 (4순위, PM 피드백 반영해 재구성) | 아래 §3-D 참조 |
| #60 | 회원 목록 조회/수정/정지/차단 | skarkgus | ⚠️ 병합 (5순위, 스키마 각색 필수) | `users.role`·`status` 참조 → 현재 DB에 없는 컬럼. member_type/admin_level로 변환 |
| #69 | 일정(event) category/description 반영, location 제거 | ahnyeji0209 | ✅ 병합 (6순위) | DDL 미적용 → DB 반영 필요 (§4) |
| #61 | boards 권한 컬럼 SQL (read_scope/write_level) | younghwan0419 | 🗄️ DB만 반영 (완료) | PM 코멘트 "열람권한·작성권한 부분만" → §4의 1)만 적용. 레포에 sql 파일 미포함(팀 컨벤션) |
| #70 | boards 미사용 컬럼 제거 SQL | ahnyeji0209 | ❌ 반려 (PM 결정) | 일시 적용했다가 PM 지시로 **원상 복원 완료**(값 백업분 그대로). 컬럼은 추후 사용 가능성 있어 유지 |

**병합 순서 근거**: #66(인증 기반) → #62(구조 개편) → #57(admin, student분 포팅) → #68(제재, #57 moderation에 연결) → #60(#66 체계로 각색) → #69(독립 도메인).

---

## 2. 중복·충돌 매트릭스

| 충돌 지점 | 관련 PR | 결정 |
|-----------|---------|------|
| `UserDto` / `AuthServicempl` / `JwtAuthenticationFilter` / `AuthMapper.xml` | #66 vs #68 | **양쪽 병합**: #66의 memberType/adminLevel + #68의 banStatus/bannedUntil·차단 검사 모두 반영 |
| `SecurityConfig` `/api/stu/**` 접근 | #66 vs dev(#51 핫픽스) | **#66 채택** (STUDENT·ALUMNI 허용). ~~⚠️ PM 재확인 포인트~~ → **논점 소멸(2차 반영)**: 신분별 URL 분기 자체가 제거되고 `boards.read_scope` 데이터 판정으로 전환됨 |
| student 게시판 매퍼 수정 (`state` 필터·소프트삭제) | #57 vs #62(파일 삭제) | #62의 `BoardMapper.xml`에 #57 의미 **포팅**: 목록/상세/top5/이전다음글 `state='normal'`, commentCount도 normal만, 댓글목록 normal만, 삭제=소프트(`state='deleted'`), **수정 시 state='normal' 조건 추가**(블라인드/삭제글 몰래 수정 방지 — 리뷰 지적 반영) |
| student 게시판 `state` 필터 방식 | #57(`= 'normal'`) vs #68(`!= 'deleted'`) | **#57 채택** (블라인드도 숨김). #68 student 변경분은 PM 지시로 제거 |
| 관리자 강제 삭제 API | #57(`/api/admin/posts/{id}` 등 moderation) vs #68(`/api/admin/free|info/...`) | **#57 채택**. #68의 free/info 강제삭제는 PM 지시로 제거 |
| 신고 수락/거절 | #57(`/api/admin/reports/{type}/{id}` 반려·삭제, 일괄, 대상단위) vs #68(`/api/admin/reports/{reportId}/resolve|reject`, 신고단위) | **#57 채택** (대상 단위 처리 + pending 일괄 종료 → 중복신고 이중벌점 원천 차단). #68의 resolve/reject 제거(PM 지시) |
| 주의→경고→정지 에스컬레이션 | #57(moderation: 주의 +2까지만) vs #68(PenaltyService: 경고/차단 자동전환) | **결합**: #57 `ModerationServiceImpl.softDelete`의 벌점 부여 직후 #68 에스컬레이션 로직 호출. #68 PenaltyService는 `admin/sanction`으로 이동·축소(중복 로그 INSERT 제거) |
| ban_log 기록 | #60(수동 정지/차단) vs #68(자동 제재) | **공존**: #60=회원관리 화면의 수동 조치, #68=자동 제재+해제/현황. 단, 신규 차단 INSERT 전 기존 활성 ban_log를 닫아 "활성 행 최대 1개" 불변식 유지(리뷰 지적 반영) |
| `checkAdminRole()` 중복 구현 | #60, #68, #69 각자 보유 | ~~각 도메인 유지~~ → **완료(2차 반영)**: `global.security.AuthUtils`로 수렴 |
| boards.code → name 변경 | #61 vs #57(`b.code` 사용) | ~~rename 미적용~~ → **완료(2차 반영, PM 지시)**: `code`→`name`(한글) rename 적용, 응답 필드 `boardName` 통일. 아래 §4 DDL의 "AFTER code" 문구는 병합 당시 기록 |

---

## 3. 브랜치 작업 체크리스트

### A. 준비
- [x] 8개 PR 본문·리뷰·인라인 코멘트 수집
- [x] qa_pilsa 실DB 스키마 대조 (mysql 직접 접속)
- [x] 중복·충돌 매트릭스 확정
- [ ] `20260814` 브랜치 생성 (base: origin/dev 51fa0dd)

### B. 병합 단계
- [ ] ① #66 병합 (SecurityConfig 충돌 → #66안 채택)
- [ ] ② #62 병합 (+ `sql/` 파일은 레포 미포함 컨벤션에 따라 제외)
- [ ] ③ #57 병합 (student 매퍼 충돌 → 삭제 유지, BoardMapper.xml에 포팅)
- [ ] ④ #68 병합 + PM 피드백 재구성:
  - [ ] student/free·info 변경분 제거 (원본은 PR 브랜치 `제재화면`에 보존됨)
  - [ ] `src/main/resources/sql/` 제거
  - [ ] report 패키지: **신고 접수만 유지** (`POST /api/stu/reports` + 중복방지 장치)
  - [ ] #68의 신고 수락/거절 제거 (#57로 대체)
  - [ ] 회원별 신고내역·신고삭제건수 → sanction으로 이동
  - [ ] sanction 패키지 → `com.back.admin.sanction`으로 이동
  - [ ] 스케줄러 매시간 → **하루 1회**로 변경
  - [ ] PenaltyService → 에스컬레이션 전용으로 축소, #57 moderation과 연결
  - [ ] UserDto/AuthServicempl/JwtAuthenticationFilter는 유지(#66과 병합)
- [ ] ⑤ #60 병합 + 각색: `status`→`member_type`(STUDENT/ALUMNI), `role`→`admin_level`(0~3) — DTO·검증·XML 전부
- [ ] ⑥ #69 병합 (docs/sql 파일은 레포 미포함으로 제외, DDL은 §4)

### C. 검증 단계
- [ ] `gradlew compileJava` / `gradlew build -x test` 성공
- [ ] 컨텍스트 로딩 (매퍼 XML 파싱 포함) 성공
- [ ] DB 추가 DDL 적용 (§4)
- [ ] API 전수 테스트 (§5) 및 결과 기록

---

## 4. DB 반영 정리 (qa_pilsa)

### 이미 적용됨 (확인 완료 — 추가 작업 불필요)
| 항목 | 출처 PR | 확인 |
|------|---------|------|
| `users.member_type`/`admin_level` 추가, `role`/`status` 제거 | #66 | DESCRIBE로 확인 |
| `comments.parent_comment_id` + FK/인덱스 | #62 | 적용됨. 단 FK가 `ON DELETE CASCADE` 없이 적용됨 — 소프트삭제 체계라 실영향 없음(물리삭제 경로 제거됨) |
| `moderation_log`·`penalty_log`·`warning_log`·`ban_log`·`ban_policy`·`reasons`·`policy_settings` | #57/#68 | 존재 + 시드값 확인 |
| `reports_log.resolved_at`/`resolution_action_id` | #68 | 존재 확인 |
| `policy_settings.cautions_per_warning` 키 표기 | #68 | DB 키와 코드 일치 확인 |

### 추가 적용 필요 (이 통합에서 실행)
```sql
-- [#69] events: category 추가, location 제거 (MySQL 8은 DROP COLUMN IF EXISTS 미지원 → 존재 확인 후 실행)
ALTER TABLE `events`
  ADD COLUMN `category` varchar(50) NULL COMMENT '일정 구분 (관리자 자유 입력, 예: 정기모임)' AFTER `title`;
ALTER TABLE `events` DROP COLUMN `location`;

-- [#61 §1만 — PM 지시로 정렬순서/이름변경 제외] boards 열람/작성권한
ALTER TABLE `boards`
  ADD COLUMN `read_scope` varchar(20) NOT NULL DEFAULT 'MEMBER'
    COMMENT '열람 대상: ALL(전체) / MEMBER(재학+졸업) / STUDENT(재학생만) / ALUMNI(졸업생만)' AFTER `code`,
  ADD COLUMN `write_level` tinyint NOT NULL DEFAULT 0
    COMMENT '작성 최소 관리레벨: 0(일반회원) / 1·2·3(관리자)' AFTER `read_scope`;
UPDATE `boards`
SET `read_scope` = 'MEMBER',
    `write_level` = CASE `board_id` WHEN 1 THEN 1 ELSE 0 END
WHERE `board_id` IN (1,2,3);
-- (원본 SQL의 'STUDENTS' 표기는 users.member_type('STUDENT')과 맞추기 위해 'STUDENT'로 통일해 주석 반영)

-- [2026-08-15] read_scope 에서 전체공개(ALL) 폐지 — 게시판은 최소 로그인 회원만 열람 가능
-- (기존 데이터에 ALL 사용 게시판 0건이라 값 마이그레이션 없이 코멘트만 정정)
ALTER TABLE `boards`
  MODIFY COLUMN `read_scope` varchar(20) NOT NULL DEFAULT 'MEMBER'
    COMMENT '열람 대상: MEMBER(재학+졸업) / STUDENT(재학생만) / ALUMNI(졸업생만) — 전체공개(ALL) 없음';

-- [2026-08-16] event_categories 는 적용 → 당일 롤백 → **재적용**(아래 CREATE 문 참고).
--               최종 방침: 테이블·시드는 PM 이 적용하고 API 는 담당자 과제.

-- [2026-08-16] 임시저장 보관 상한 5개 확정 (PM 지시 — 코드는 하드코딩 대신 이 값을 로드할 것)
INSERT INTO `policy_settings` (code, setting_value, description)
VALUES ('draft_max_count', '5', '임시저장 보관 상한 (회원당 게시판별)');

-- [2026-08-16] 탈퇴 후 재가입 쿨다운 (계정 양산 어뷰징 방지)
INSERT INTO `policy_settings` (code, setting_value, description)
VALUES ('rejoin_cooldown_days', '30', '탈퇴 후 재가입 대기 일수 (계정 양산 어뷰징 방지)');

-- [2026-08-16] 이력 없는 탈퇴 행 자동 정리 (새벽 04:30 배치, WithdrawnUserPurgeScheduler)
INSERT INTO `policy_settings` (code, setting_value, description)
VALUES ('withdrawn_purge_days', '90', '활동·제재 이력 없는 탈퇴 행 보존 일수 (경과 시 새벽 배치가 물리 삭제)');

-- [2026-08-16] 이메일 인증 통과 플래그 유효시간을 정책으로 (MailServiceImpl 이 로드, 기본 30)
INSERT INTO `policy_settings` (code, setting_value, description)
VALUES ('mail_verified_ttl_minutes', '30', '이메일 인증 통과 플래그 유효시간(분) — 만료 후 가입/비밀번호 초기화 시도 시 재인증 안내');

-- [2026-08-16] 회원가입 입력 형식 정책 — 프론트(pilsa-frontend schemas/auth.js zod)와 동일 규칙.
-- 백슬래시 이스케이프 사고 방지를 위해 \d 대신 [0-9] 표기 사용. 코드 기본값과 동일(AuthServicempl.validateSignupFormat)
INSERT INTO `policy_settings` (code, setting_value, description) VALUES
('signup_name_regex',       '^[a-zA-Zㄱ-ㅎ가-힣]{2,50}$',                          '가입 이름 형식 (2자 이상, 한글/영문)'),
('signup_student_no_regex', '^[0-9]{10}$',                                        '가입 학번 형식 (숫자 10자리)'),
('signup_login_id_regex',   '^[a-zA-Z0-9]{8,50}$',                                '가입 아이디 형식 (8자 이상, 영문+숫자)'),
('signup_password_regex',   '^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,20}$','가입 비밀번호 형식 (문자+숫자+특수문자 8~20자)'),
('signup_phone_regex',      '^010-[0-9]{4}-[0-9]{4}$',                            '가입 전화번호 형식 (010-0000-0000)'),
('signup_email_regex',      '^[^@ ]+@[^@ ]+[.][^@ ]+$',                           '가입 이메일 형식');

-- [2026-08-16] 신고 사유에 아동 안전 추가 (Google Play 아동 안전 표준 — 신고 경로 명시 요건)
-- 음란(ADULT) 바로 뒤에 배치. ETC 는 항상 마지막 유지
UPDATE `reasons` SET display_order = display_order + 1 WHERE display_order >= 4;
INSERT INTO `reasons` (code, label, display_order, is_active)
VALUES ('CHILD_SAFETY', '아동 안전 위반 · 아동 성착취물', 4, 1);

-- [2026-08-16] 일정 카테고리 정본 테이블 재생성 (PM 지시: 테이블은 PM 이, API 는 담당자가)
CREATE TABLE `event_categories` (
  `event_category_id` bigint NOT NULL AUTO_INCREMENT COMMENT '일정 카테고리 고유 번호',
  `name` varchar(50) NOT NULL COMMENT '카테고리명 (화면 노출 한글명)',
  `display_order` int NOT NULL DEFAULT 0 COMMENT '노출 순서',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '사용 여부 (0=숨김, 물리삭제 없음)',
  PRIMARY KEY (`event_category_id`),
  UNIQUE KEY `uq_event_categories_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='일정 카테고리 (events.category 값의 정본)';
INSERT INTO `event_categories` (name, display_order)
VALUES ('정기모임', 1), ('MT', 2), ('행사', 3), ('스터디', 4), ('기타', 99);

-- [2026-08-16] 알림함 표시 기간 (PM 확정: 페이징 없이 전체 반환하되 최근 N개월만)
INSERT INTO `policy_settings` (code, setting_value, description)
VALUES ('notification_list_months', '2', '알림함에 보여줄 기간(개월) — 목록은 페이징 없이 이 기간만 전체 반환');

-- [2026-08-16] api_endpoints 에 스웨거 실테스트 확정일 컬럼 추가 (PM 수동 기록용)
ALTER TABLE `api_endpoints`
  ADD COLUMN `confirmed_at` date DEFAULT NULL
    COMMENT '스웨거 실테스트 통과 확정일 (수동 입력). NULL 이거나 오늘 날짜가 아니면 미확인' AFTER `status`;

-- [2026-08-16] 스웨거 전수 테스트용 계정 10개 시드 (t_stu ~ t_del, user_id 96~105)
-- 비밀번호 해시는 wm5256 과 동일하게 복사 (동일 비밀번호로 로그인). 상황: 재학/졸업/관리자Lv1~3/정지중/영구차단/정지만료/탈퇴
-- 상세는 docs/integration-20260814/TEST-PLAN.md §1

-- [2026-08-16] 알림 수신 기기 등록부 (웹 푸시 채널 — PM 지시로 2기 개발). 세션성 데이터라 물리삭제 예외
CREATE TABLE `notification_devices` (
  `device_id`   bigint       NOT NULL AUTO_INCREMENT COMMENT '알림 수신 기기 고유 번호',
  `user_id`     bigint       NOT NULL COMMENT '기기 소유 회원 (→users)',
  `endpoint`    varchar(500) NOT NULL COMMENT '브라우저가 발급한 푸시 수신 주소',
  `p256dh`      varchar(255) NOT NULL COMMENT '페이로드 암호화 공개키 (브라우저 발급)',
  `auth_secret` varchar(255) NOT NULL COMMENT '페이로드 암호화 인증 시크릿 (브라우저 발급)',
  `created_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 일시',
  PRIMARY KEY (`device_id`),
  UNIQUE KEY `uq_notification_devices_endpoint` (`endpoint`),
  KEY `idx_notification_devices_user` (`user_id`),
  CONSTRAINT `fk_notification_devices_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='알림 수신 기기 등록부 (웹 푸시. 세션성 데이터 — 물리 삭제 허용)';


-- [2026-08-18] 통계 3종 (PM 적용 완료 — 아래는 적용된 정본 기록).
--   설계 요점: 스냅샷·구간 테이블을 따로 두지 않는다. 누적값은 집계 행에, 접속자 수는 원본에서 얻는다.
--   접속은 단위별 테이블 없이 stats_access_hourly 하나를 GROUP BY 해 일·주·월·학기·연을 만든다.
--   가입만 스냅샷을 남긴다 — 탈퇴 90일 정리 배치가 users 행을 물리 삭제해 과거 수치가 소급 감소하고,
--   member_type 도 졸업 시 바뀌기 때문에 지금 고정하지 않으면 소실된다.
CREATE TABLE `stats_access_hourly` (
  `user_id`     bigint   NOT NULL COMMENT '접속 회원 (FK 없음 — 탈퇴 후에도 통계 보존)',
  `access_hour` datetime NOT NULL COMMENT '시간 버킷 (분·초 절삭, 예: 2026-08-18 21:00:00)',
  PRIMARY KEY (`user_id`,`access_hour`),
  KEY `idx_stats_access_hourly_hour` (`access_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원 접속 기록 (시간 버킷당 1행)';

CREATE TABLE `stats_signup_weekly` (
  `stat_week`     date     NOT NULL COMMENT '주 시작일(월요일)',
  `signup_count`  int      NOT NULL DEFAULT 0 COMMENT '신규가입 수 (탈퇴자 포함 — 가입 사실은 변하지 않는다)',
  `student_count` int      NOT NULL DEFAULT 0 COMMENT '그중 재학생 (집계 시점 member_type 스냅샷)',
  `alumni_count`  int      NOT NULL DEFAULT 0 COMMENT '그중 졸업생',
  `captured_at`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '이 행을 집계한 시각',
  PRIMARY KEY (`stat_week`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='주간 신규가입 통계 (8주치 백필 완료)';

-- stats_post_hourly: 구간 집계 + 급상승 판정. 변화가 적재 컷 이상인 글만 행이 생긴다(행 폭증 방지).
--   read_scope 는 집계 당시 스냅샷 — 조회 API 가 게시판 권한 필터를 걸 때 boards 재조인 없이 쓰기 위한 것.
CREATE TABLE `stats_post_hourly` (
  `stat_hour`      datetime      NOT NULL COMMENT '집계 구간 시작 (시간 버킷)',
  `post_id`        bigint        NOT NULL COMMENT '대상 게시글 (FK 없음 — 통계 보존)',
  `board_id`       bigint        NOT NULL COMMENT '대상 게시판 (→boards)',
  `read_scope`     varchar(20)   NOT NULL COMMENT '집계 당시 열람 범위 스냅샷: MEMBER / STUDENT / ALUMNI',
  `view_count`     int           NOT NULL DEFAULT 0 COMMENT '집계 시점 누적 조회수 (다음 집계의 기준선)',
  `like_count`     int           NOT NULL DEFAULT 0 COMMENT '집계 시점 좋아요 수',
  `comment_count`  int           NOT NULL DEFAULT 0 COMMENT '집계 시점 공개 댓글 수 (state=normal, is_private=0)',
  `view_delta`     int           NOT NULL DEFAULT 0 COMMENT '직전 행 이후 조회 증가분',
  `like_delta`     int           NOT NULL DEFAULT 0 COMMENT '직전 행 이후 좋아요 순증 (취소 시 음수)',
  `comment_delta`  int           NOT NULL DEFAULT 0 COMMENT '직전 행 이후 공개 댓글 순증',
  `raw_score`      decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '가중 합산 (조회×1 + 좋아요×5 + 댓글×3)',
  `baseline_score` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '평소 수준 = 직전 N구간 raw_score 의 SUM/N (행 평균 아님)',
  `spike_ratio`    decimal(8,2)  DEFAULT NULL COMMENT '평소 대비 배수. NULL = 이력 없음(신규 글, 관문2 면제)',
  `freshness`      decimal(6,4)  NOT NULL DEFAULT 1.0000 COMMENT '글 나이 감쇠 (1=방금, 0.5=하루 경과)',
  `final_score`    decimal(12,4) NOT NULL DEFAULT 0.0000 COMMENT 'raw_score / 접속자수보정 × freshness',
  `rank_no`        int           DEFAULT NULL COMMENT '구간 내 순위 (1위부터)',
  `is_trending`    tinyint(1)    NOT NULL DEFAULT 0 COMMENT '급상승 선정 여부 (관문 1·2·3 통과)',
  PRIMARY KEY (`stat_hour`,`post_id`),
  KEY `idx_stats_post_hourly_selected` (`stat_hour`,`is_trending`,`rank_no`),
  KEY `idx_stats_post_hourly_post` (`post_id`,`stat_hour`),
  KEY `idx_stats_post_hourly_board` (`board_id`,`stat_hour`),
  CONSTRAINT `fk_stats_post_hourly_board` FOREIGN KEY (`board_id`) REFERENCES `boards` (`board_id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 구간 집계 + 급상승 판정 (변화가 기준치 이상인 글만 적재)';

-- 집계 대상 게시판을 데이터로 고른다 (게시판 하드코딩 금지 규칙과 같은 이유 — 신설 게시판은 기본 포함)
ALTER TABLE `boards`
  ADD COLUMN `trending_enabled` tinyint(1) NOT NULL DEFAULT 1
    COMMENT '급상승 집계 대상 여부 (1=포함, 0=제외). 신설 게시판은 기본 포함';

-- [2026-08-18] 통계 정책 수치. 코드(StatsPolicy)에 동일 기본값이 있어 행이 없어도 동작한다 —
--   행을 넣으면 재배포 없이 값만 바꿔 조정할 수 있다.
INSERT INTO `policy_settings` (code, setting_value, description) VALUES
('trending_interval_minutes',      '60',   '급상승 집계 주기(분). 다음 실행부터 반영'),
('trending_post_max_age_hours',    '168',  '급상승 후보 최대 글 나이(시간, 기본 7일)'),
('trending_min_delta_score',       '5',    '적재 컷 — raw_score 미달 구간은 행을 만들지 않는다'),
('trending_baseline_windows',      '6',    '평소 수준 산출에 쓰는 직전 구간 수 (합/N, 행 평균 아님)'),
('trending_min_score',             '10',   '급상승 관문1 — 절대 활동량 하한'),
('trending_spike_ratio',           '3.0',  '급상승 관문2 — 평소 대비 배수. 이력 없는 신규 글은 면제'),
('trending_top_n',                 '5',    '급상승 관문3 — 구간 내 상위 N'),
('trending_weight_view',           '1',    'raw_score 조회 가중치'),
('trending_weight_like',           '5',    'raw_score 좋아요 가중치'),
('trending_weight_comment',        '3',    'raw_score 댓글 가중치 (공개 댓글만 집계)'),
('trending_active_user_floor',     '5',    '점수 분모 하한 — 새벽 소수 접속 구간의 점수 폭등 방지'),
('trending_freshness_scale_hours', '24',   'freshness = 1/(1 + 글나이/이 값). 이 시간 경과 시 0.5'),
('signup_stats_recalc_weeks',      '2',    '주간 가입 통계 재집계 구간(주)'),
('stats_retention_days',           '1825', '접속·게시글 집계 보존 일수(5년). 경과 행은 새벽 배치가 물리 삭제');
```
- [x] events DDL 적용 (2026-08-14, location 값 전부 NULL 확인 후 제거)
- [x] **통계 3종 테이블 + `boards.trending_enabled` 적용** (2026-08-18 PM). 수집·집계 코드는 `통계` 브랜치의 `com.back.stats`.
  정책 14행은 **선택** — `StatsPolicy` 에 동일 기본값이 있어 넣지 않아도 동작하고, 넣으면 재배포 없이 조정된다.
  조회 API 는 아직 없다(설계는 `SPEC-stats.md` §5).
- [x] boards 권한 컬럼 DDL 적용 (#61 §1)
- [x] ~~boards 컬럼 제거 (#70)~~ → 적용 후 **PM 지시로 원복 완료** (allow_comment/allow_attachment/category_mode 원값 유지)
- [x] 적용 후 스키마 재검증 (DESCRIBE 확인)
- [x] **api_endpoints 테이블 생성 + 95행 시드** (2026-08-14 PM 지시) — API 인벤토리 정본.
  `phase`(1기=6월 이전 / 2기) × `status`(active=구현·검증 완료 70 / planned=예정 25) × `auth`(PUBLIC/MEMBER/ADMIN).
  경로 변경 이력은 note 컬럼에 기록. 노션 명세와 동기 대상.

### 보류/참고
- ~~`read_scope`/`write_level`은 아직 코드에서 미사용~~ → **2차 반영으로 사용 중**: 모든 게시판 요청의
  열람·작성 판정에 사용된다 (`BoardPolicy.canRead/canWrite` ← `BoardPolicyService.requireReadable/requireWritable`).
- comments FK `ON DELETE CASCADE` 미부여: 전 경로 소프트삭제 전환으로 물리삭제 없음 → 변경 불필요.

---

## 5. API 테스트 결과 (2026-08-14 로컬 기동 + qa_pilsa 실DB, 테스트 데이터는 검증 후 정리 완료)

| # | API | 출처 | 상태 |
|---|-----|------|------|
| 로그인/토큰 (#66,#68) |
| 1 | POST /api/auth/login — memberType/adminLevel 응답 | #66 | ✅ |
| 2 | POST /api/auth/token/access/refresh — 리프레시 회전(rotated 확인) | #66 | ✅ |
| 3 | GET /api/role — memberType/adminLevel | #66 | ✅ |
| 4 | 차단회원 로그인 403(+해제예정일) / 세션 중 요청 403 + X-Ban-Type | #68 | ✅ |
| 4-1 | 회원가입 memberType 화이트리스트 (ADMIN 등 거부 400) | 보강 | ✅ |
| 4-2 | 미인증 요청 401 / 권한부족 403 구분 (게시글 등록 403 혼선 해결) | 보강 | ✅ |
| 4-3 | ALUMNI 계정(/api/stu 접근·게시글 등록) — PM 계정(wm5256) 실검증 | #66 | ✅ |
| 게시판 통합 (#62 + #57 포팅) |
| 5 | GET /api/stu/{1,2,3}/posts (페이징/검색, state=normal 필터) | #62 | ✅ |
| 6 | GET /api/stu/{b}/posts/{id} (상세·댓글·대댓글 parentId) | #62 | ✅ |
| 7 | POST /api/stu/{b}/posts (multipart, 공지=관리자만: 학생 403/관리자 성공) | #62 | ✅ |
| 8 | PUT/DELETE 게시글 — 소프트삭제(state=deleted), blind글 작성자 수정 404 차단 | #62+#57 | ✅ |
| 9 | POST 댓글/대댓글(잘못된 부모 400), PUT/DELETE 댓글(소프트, 삭제부모 숨김+답글 잔존) | #62 | ✅ |
| 10 | PATCH 좋아요 토글(liked/likeCount) / categories | #62 | ✅ |
| 관리자 게시글/신고 (#57) |
| 11 | GET /api/admin/posts (keyword 검색·페이징) | #57 | ✅ |
| 12 | GET /api/admin/posts/{id} (blind 상태 글 열람, 실작성자명) | #57 | ✅ |
| 13 | PATCH blind(학생 화면 404 확인) / restore(재노출 확인) | #57 | ✅ |
| 14 | POST bulk-delete — 4성공/1실패(없는 id) 부분 성공 | #57 | ✅ |
| 15 | GET /api/admin/reports/posts (그룹핑·사유·건수·state) | #57 | ✅ |
| 16 | 신고 삭제(resolved+resolution_action_id 연결) / 반려(rejected, 대상 normal 유지) | #57 | ✅ |
| 신고 접수 (#68) |
| 17 | POST /api/stu/reports — 접수 + 중복 신고 409 | #68 | ✅ |
| 제재 (#68 재구성) |
| 18 | 삭제 5건 → 주의 10pt → 경고 1 → BAN_W1(7일 temporary) 자동 발동 (ban_log/users 캐시 확인) | #68 | ✅ |
| 19 | GET /api/admin/sanctions/users, /{id} — tag/cautionRemainder/warningCount/reportDeletedCount | #68 | ✅ |
| 20 | POST /api/admin/sanctions/users/{id}/lift — 해제(lifted_by 기록) 후 재로그인 성공 | #68 | ✅ |
| 21 | GET /api/admin/sanctions/users/{id}/reports (이동된 회원별 신고내역) | #68 | ✅ |
| 회원 관리 (#60 각색) |
| 22 | GET /api/admin/members — 검색/게시글·댓글수/정지기간(banStartAt~banEndAt)/memberType/adminLevel | #60 | ✅ |
| 23 | PUT /api/admin/members/{id} — 부분수정 성공, WIZARD·adminLevel=9 → 400 | #60 | ✅ |
| 24 | suspend(과거일 400/성공/영구차단자 409), ban(다중·중복id 정리, 없는 id 404 전체실패), 학생 접근 403 | #60 | ✅ |
| 일정 (#69) |
| 25 | POST(201)/PUT/DELETE /api/admin/schedules — category/description 반영, 학생 403 | #69 | ✅ |
| 26 | GET /api/public/schedules?from=YYYY-MM&to=YYYY-MM — 비로그인 조회 | #69 | ✅ |

**전 항목 통과.** 발견·수정된 이슈: ①회원가입 memberType 무검증(권한상승) ②미인증 403 혼선 ③리네임 치환 손상(경로/필드) — 모두 수정 커밋 완료.

---

## 6. PM 재확인 포인트 — **전 항목 종결 (2026-08-14 2차 반영)**
1. ~~`/api/stu/**`에 ALUMNI 허용~~ → **폐기**: 신분별 URL 분기 제거. 열람 대상은 `boards.read_scope` 데이터로 판정 (REVIEW-NOTES §6).
2. ~~일반 회원 isPinned 설정 가능~~ → **해결**: 모든 게시판에서 관리자(레벨 1~3)만 설정 가능 (`resolvePinned`, 실측 검증).
3. ~~ban_log 수동/자동 혼재~~ → **해결**: `source`(auto/manual) 컬럼 + `warning_no` nullable — 수동 조치가 경고로 집계되지 않음.
4. ~~신고 접수 경로 /api/stu/reports ALUMNI 연동~~ → **폐기**: 최종 경로 `POST /api/user/reports` (신분·관리자 무관 공통).

> §5 테스트 표의 경로들은 **병합 당시(1차) 경로**다. 이후 URL 재설계로 전부 변경됨 — 현행 경로는 `API-MIGRATION.md` 참조.
