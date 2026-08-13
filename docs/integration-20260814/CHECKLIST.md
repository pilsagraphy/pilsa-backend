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
| `SecurityConfig` `/api/stu/**` 접근 | #66 vs dev(#51 핫픽스) | **#66 채택** (STUDENT·ALUMNI 허용). #51 핫픽스가 ALUMNI를 제외했었으나, 권한 개편 전담 PR(#66)의 명시 설계 + #61의 read_scope='MEMBER' 초기값과 일치. ⚠️ PM 재확인 포인트 |
| student 게시판 매퍼 수정 (`state` 필터·소프트삭제) | #57 vs #62(파일 삭제) | #62의 `BoardMapper.xml`에 #57 의미 **포팅**: 목록/상세/top5/이전다음글 `state='normal'`, commentCount도 normal만, 댓글목록 normal만, 삭제=소프트(`state='deleted'`), **수정 시 state='normal' 조건 추가**(블라인드/삭제글 몰래 수정 방지 — 리뷰 지적 반영) |
| student 게시판 `state` 필터 방식 | #57(`= 'normal'`) vs #68(`!= 'deleted'`) | **#57 채택** (블라인드도 숨김). #68 student 변경분은 PM 지시로 제거 |
| 관리자 강제 삭제 API | #57(`/api/admin/posts/{id}` 등 moderation) vs #68(`/api/admin/free|info/...`) | **#57 채택**. #68의 free/info 강제삭제는 PM 지시로 제거 |
| 신고 수락/거절 | #57(`/api/admin/reports/{type}/{id}` 반려·삭제, 일괄, 대상단위) vs #68(`/api/admin/reports/{reportId}/resolve|reject`, 신고단위) | **#57 채택** (대상 단위 처리 + pending 일괄 종료 → 중복신고 이중벌점 원천 차단). #68의 resolve/reject 제거(PM 지시) |
| 주의→경고→정지 에스컬레이션 | #57(moderation: 주의 +2까지만) vs #68(PenaltyService: 경고/차단 자동전환) | **결합**: #57 `ModerationServiceImpl.softDelete`의 벌점 부여 직후 #68 에스컬레이션 로직 호출. #68 PenaltyService는 `admin/sanction`으로 이동·축소(중복 로그 INSERT 제거) |
| ban_log 기록 | #60(수동 정지/차단) vs #68(자동 제재) | **공존**: #60=회원관리 화면의 수동 조치, #68=자동 제재+해제/현황. 단, 신규 차단 INSERT 전 기존 활성 ban_log를 닫아 "활성 행 최대 1개" 불변식 유지(리뷰 지적 반영) |
| `checkAdminRole()` 중복 구현 | #60, #68, #69 각자 보유 | 각 도메인 유지 (기능 동일, 리팩터링은 후속) |
| boards.code → name 변경 | #61 vs #57(`b.code` 사용) | **rename 미적용** (PM: 권한 부분만). `code` 유지 → #57 쿼리 안전 |

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

```
- [x] events DDL 적용 (2026-08-14, location 값 전부 NULL 확인 후 제거)
- [x] boards 권한 컬럼 DDL 적용 (#61 §1)
- [x] ~~boards 컬럼 제거 (#70)~~ → 적용 후 **PM 지시로 원복 완료** (allow_comment/allow_attachment/category_mode 원값 유지)
- [x] 적용 후 스키마 재검증 (DESCRIBE 확인)

### 보류/참고
- `read_scope`/`write_level`은 아직 **코드에서 미사용** (게시판관리 화면 후속 작업용 선반영).
- comments FK `ON DELETE CASCADE` 미부여: 전 경로 소프트삭제 전환으로 물리삭제 없음 → 변경 불필요.

---

## 5. API 테스트 계획 (dev 대비 신규/변경 전부)

| # | API | 출처 | 상태 |
|---|-----|------|------|
| 로그인/토큰 (#66,#68) |
| 1 | POST /api/auth/login — memberType/adminLevel 응답 | #66 | [ ] |
| 2 | POST /api/auth/token/access/refresh — 리프레시 회전 | #66 | [ ] |
| 3 | GET /api/role — memberType/adminLevel | #66 | [ ] |
| 4 | 차단회원 로그인 403 / 요청 차단(X-Ban-Type) | #68 | [ ] |
| 게시판 통합 (#62 + #57 포팅) |
| 5 | GET /api/stu/{1,2,3}/posts (페이징/검색/정렬, state 필터) | #62 | [ ] |
| 6 | GET /api/stu/{b}/posts/{id} (상세, 대댓글 포함) | #62 | [ ] |
| 7 | POST /api/stu/{b}/posts (공지=관리자만, 첨부) | #62 | [ ] |
| 8 | PUT/DELETE 게시글 (소프트삭제 확인, blind글 수정 차단) | #62+#57 | [ ] |
| 9 | POST 댓글/대댓글, PUT/DELETE 댓글(소프트) | #62 | [ ] |
| 10 | PATCH 좋아요 토글 / top5 / categories | #62 | [ ] |
| 관리자 게시글/신고 (#57) |
| 11 | GET /api/admin/posts (필터/검색/페이징, deleted 제외) | #57 | [ ] |
| 12 | GET /api/admin/posts/{id} (blind/deleted도 열람) | #57 | [ ] |
| 13 | PATCH blind / restore, DELETE (사유·벌점, 신고 동기화) | #57 | [ ] |
| 14 | POST bulk-delete (부분 성공) | #57 | [ ] |
| 15 | GET /api/admin/reports/posts·comments (그룹핑/필터) | #57 | [ ] |
| 16 | PATCH reject / DELETE / bulk (대상단위, pending 일괄 종료) | #57 | [ ] |
| 신고 접수 (#68) |
| 17 | POST /api/stu/reports (중복 신고 409) | #68 | [ ] |
| 제재 (#68 재구성) |
| 18 | 삭제 누적 → 주의(+2)→경고(10pt)→정지(1주/1달)/영구 자동 전환 | #68 | [ ] |
| 19 | GET /api/admin/sanctions/users, /{id} (태그/누적 수치) | #68 | [ ] |
| 20 | POST /api/admin/sanctions/users/{id}/lift (수동 해제) | #68 | [ ] |
| 21 | 회원별 신고내역 GET (sanction으로 이동한 API) | #68 | [ ] |
| 회원 관리 (#60 각색) |
| 22 | GET /api/admin/members (검색/정렬/페이징/게시글·댓글수/정지기간) | #60 | [ ] |
| 23 | PUT /api/admin/members/{id} (부분수정·중복검사·memberType/adminLevel) | #60 | [ ] |
| 24 | POST /{id}/suspend, POST /ban (단일/다중) | #60 | [ ] |
| 일정 (#69) |
| 25 | POST/PUT/DELETE /api/admin/schedules (category/description) | #69 | [ ] |
| 26 | GET /api/public/schedules?from&to (월 단위 변환) | #69 | [ ] |

---

## 6. PM 재확인 포인트 (병합은 진행하되 최종 승인 전 확인 권장)
1. `/api/stu/**`에 ALUMNI 허용(#66 설계 채택) — #51 핫픽스와 상반. 졸업생의 재학생 게시판 열람 정책 확정 필요.
2. `isPinned`를 자유/정보 게시판에서 일반 회원이 설정 가능(#62, PM 보류 결정 유지) — 후속 PR 권장.
3. #60의 수동 정지(warning_no=1 재사용)와 #68 자동 제재의 ban_log 혼재 — 현재는 공존 설계, 통계 분리 필요 시 후속.
4. 신고 접수 경로가 `/api/stu/reports`라 ALUMNI 정책과 연동됨(1번과 동일 축).
