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

-- [2026-08-16] event_categories(일정 카테고리 테이블)는 적용했다가 **PM 지시로 당일 롤백** —
--               팀원 과제로 전환. 완성 구현본(DDL+시드+API+검증)은 git 브랜치 archive/event-categories 에 보관.

-- [2026-08-16] 임시저장 보관 상한 5개 확정 (PM 지시 — 코드는 하드코딩 대신 이 값을 로드할 것)
INSERT INTO `policy_settings` (code, setting_value, description)
VALUES ('draft_max_count', '5', '임시저장 보관 상한 (회원당 게시판별)');

-- [2026-08-16] 탈퇴 후 재가입 쿨다운 (계정 양산 어뷰징 방지)
INSERT INTO `policy_settings` (code, setting_value, description)
VALUES ('rejoin_cooldown_days', '30', '탈퇴 후 재가입 대기 일수 (계정 양산 어뷰징 방지)');

-- [2026-08-16] 이력 없는 탈퇴 행 자동 정리 (새벽 04:30 배치, WithdrawnUserPurgeScheduler)
INSERT INTO `policy_settings` (code, setting_value, description)
VALUES ('withdrawn_purge_days', '90', '활동·제재 이력 없는 탈퇴 행 보존 일수 (경과 시 새벽 배치가 물리 삭제)');

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

```
- [x] events DDL 적용 (2026-08-14, location 값 전부 NULL 확인 후 제거)
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
