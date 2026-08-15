# CLAUDE.md — pilsa-backend

필사(pilsagraphy) 동아리 홈페이지 백엔드. Spring Boot 3.4 (Java 17) + MyBatis + MySQL 8 + Redis + JWT.

## 빌드/실행
```bash
./gradlew compileJava          # 컴파일 검증
./gradlew build -x test        # 빌드
./gradlew bootRun              # 실행 (기본 8080)
```
- DB: `application.properties`의 qa_pilsa (원격 MySQL). Flyway/Liquibase **없음** — DDL은 DB에 수동 적용하며,
  **.sql 파일을 레포에 커밋하지 않는 것이 팀 컨벤션**. 적용한 DDL은 `docs/integration-*/CHECKLIST.md`에 기록.
- Redis: 리프레시 토큰. Swagger: `/swagger-ui/index.html`.

## 패키지 구조 — **UI 페이지 단위 또는 DB 테이블 단위**로 맞춘다
```
com.back
├── auth            # 로그인/회원가입/토큰 + 신분·권한 조회(GET /api/role) + 이메일 인증번호(/api/mail/**)
├── board           # 게시판 (posts/comments/boards). 게시판은 데이터로 정의됨
│   └── report      # 신고 접수 (POST /api/user/reports) — 게시글/댓글에 대한 회원 기능이라 board 하위
├── mypage          # 마이페이지 — notification(헤더 종 아이콘 + 알림 수신 기기 등록부=웹 푸시)
├── event           # 일정 조회(회원 달력, 공개) + 구글 캘린더 구독 피드(/api/event/calendar.ics)
├── donation        # 명예의전당 (donations)
├── admin           # 관리자 화면 전용 도메인
│   ├── common      # AdminServiceSupport, BulkResultResponse(부분 성공)
│   ├── event       # 일정 등록/수정/삭제 (매퍼는 event 도메인의 EventMapper 공유)
│   ├── board       # 게시판 관리 (생성/수정/권한 설정)
│   ├── post        # 게시글 관리 (조회 전용 — 조치는 신고 관리 select-*)
│   ├── moderation  # 게시글·댓글 공통 조치(blind/restore/softDelete) + moderation_log/penalty_log
│   ├── quote       # 이 주의 문장 — 공개 랜덤(/api/quotes/current)도 예외적으로 여기 소속(PM 허용)
│   ├── sanction    # 제재 현황/해제 + 주의→경고→정지 에스컬레이션 + 신고 처리(ReportAdminService, select-*)
│   └── user        # 회원 관리 (users)
└── global          # 인프라 계층만: config, security(JWT·AuthUtils), util(FileStorageUtil), exception
```
매퍼 XML: `src/main/resources/mapper/<도메인 경로>/*.xml`.

## 핵심 도메인 규칙

### 권한은 URL이 아니라 **데이터**로 판정한다
- 사용자 2축: `users.member_type`(STUDENT/ALUMNI) + `users.admin_level`(0=일반, 1~3=관리자).
  JWT 필터가 매 요청 DB 최신값으로 `ROLE_STUDENT|ROLE_ALUMNI`, `ROLE_ADMIN`, `ADMIN_LV_{n}` 부여.
- **URL에 신분(stu/alu) 접두사를 쓰지 않는다.** 회원 API는 `/api/user/**` 하나로 묶는다 —
  게시판은 `/api/user/boards/{boardId}/**`, 내 정보는 `/api/user/mypage/**`, 신고는 `/api/user/reports`.
  공개 리소스는 `/api/donations`·`/api/quotes/current`·`/api/event`. 관리자는 `/api/admin/**`.
  경로 정본은 `api_endpoints` 테이블이며, 코드가 그 표기를 따른다.
  전체 매핑표는 `docs/integration-20260814/API-MIGRATION.md`.
- SecurityConfig는 `/api/admin/**`=ADMIN, 그 외 회원 API는 **로그인 여부만** 확인.
  신분별 접근은 각 도메인이 `AuthUtils`(global.security)로 판정한다. 신분을 URL 접두사로 가르지 않는다 —
  관리자가 런타임에 만든 게시판의 열람 대상을 정적 URL로 표현할 수 없기 때문.
- 게시판: `boards.read_scope`(MEMBER=재학+졸업 / STUDENT=재학 / ALUMNI=졸업) + `boards.write_level`(0~3).
  **전체 공개(ALL)는 없다** — 게시판은 최소 로그인 회원이어야 열람 가능하며, 비로그인 공개는 게시판이 아닌 공개 리소스만.
  판정 로직은 `BoardPolicy.canRead/canWrite`, 진입점은 `BoardPolicyService.requireReadable/requireWritable`.
- **인증 코드 중복 금지**: 현재 사용자 id·관리자 여부·관리레벨은 전부 `AuthUtils`를 쓴다.

### 게시판은 하드코딩하지 않는다
- `BoardType` enum은 **제거됨**. 게시판별 정책(열람·작성 권한, 익명/비밀댓글/첨부/카테고리 사용, 기본 카테고리,
  노출 순서)은 전부 `boards` 테이블 컬럼이며 `BoardPolicy`로 읽는다.
- 관리자가 `/api/admin/boards`로 게시판을 만들면 코드 수정·재배포 없이 `/api/user/boards/{boardId}/**`가 즉시 동작한다.
- 프론트는 게시판 메뉴를 하드코딩할 수 없다 — `GET /api/user/boards`(열람 가능 게시판 목록, canWrite 포함)로 그린다.
- `boards.name`은 화면에 그대로 노출하는 **한글명**(공지사항/자유게시판/정보게시판).
  게시판명을 담는 응답 필드는 어디서든 `boardName`으로 통일 (게시판 목록·관리자 게시글·신고·제재 응답 모두).
- `is_pinned`(상단 고정)는 게시판 종류와 무관하게 **관리자(admin_level≥1)만** 설정 가능.

### 소프트삭제가 대전제 — 물리 삭제는 없다
- posts/comments/events/quotes/boards/notifications 모두 `state`(normal/blind/deleted) 또는 그에 준하는 컬럼 사용.
- 유일한 예외는 `post_likes`(좋아요 토글의 본질이 행 삭제)와 세션성 데이터.
- 학생 화면은 `state='normal'`만. 블라인드·삭제된 콘텐츠는 작성자도 수정 불가(증적 보호).
- 관리자 조치는 반드시 `ModerationService` 경유(로그+벌점 일관성).

### 신고와 제재
- **신고는 관리자든 일반 회원이든 동일하게 `POST /api/user/reports`로 접수**한다.
  관리자가 특별한 점은 신고 없이 곧바로 조치할 수 있다는 것뿐이며, 그 조치는 `admin.moderation`이 담당한다.
- 신고 처리(반려/삭제)는 **대상 단위**로 pending 전부 일괄 종료 — 동일 대상 중복 신고로 벌점이 이중 부과되지 않게 하는 장치이므로 유지할 것.
- 제재: 관리자 삭제 → penalty +2(`caution_per_delete`) → 유효합 10점당 경고 1회(`cautions_per_warning`)
  → 경고 횟수별 `ban_policy`(1주/1달/영구, **3단계 확정**). 수치는 policy_settings에서 로드.
- `ban_log.source`: `auto`(경고 누적) / `manual`(관리자 직접, warning_no=NULL). 수동 조치가 경고로 집계되지 않게 구분.
- users.ban_status/banned_until은 캐시 — 판정은 항상 banned_until 실시간 비교, 스케줄러(일 1회 04시)는 캐시 정리만.

## 주의사항
- `users.role`/`status` 컬럼은 **DB에서 제거됨** — 참조 시 런타임 SQL 오류.
- **`boolean isXxx` 필드 금지, `Boolean isXxx` 사용**: primitive면 자바 빈 프로퍼티명이 `xxx`가 되어
  폼 키 `isPinned`가 바인딩되지 않고 응답 JSON도 `pinned`으로 나간다(실제 발생했던 버그).
- 에러 응답은 항상 JSON 객체 `{"message": ...}`. 정지/차단은 `banType`,`bannedUntil` 필드가 추가된다.
- 미인증=401, 권한부족=403 (SecurityConfig의 exceptionHandling).
- MyBatis 파라미터 2개 이상이면 @Param 필수. 날짜는 문자열 'YYYY-MM-DD'로 받는 API 다수.
- 패키지/클래스 일괄 리네임 시 PowerShell `-replace`는 대소문자 무시라 URL·컬럼명까지 깨뜨린다. `-creplace` 사용 후 경로·필드·SQL 잔존 검사 필수.
- 통합 이력·미해결 결정사항: `docs/integration-20260814/` (CHECKLIST / REVIEW-NOTES / BACKLOG).
