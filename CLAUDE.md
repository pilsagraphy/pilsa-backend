# CLAUDE.md — pilsa-backend

필사(pilsagraphy) 동아리 홈페이지 백엔드. Spring Boot 3.4 (Java 17) + MyBatis + MySQL 8 + Redis + JWT.

## 빌드/실행
```bash
./gradlew compileJava          # 컴파일 검증
./gradlew build -x test        # 빌드
./gradlew bootRun              # 실행 (기본 8080)
```
- DB: `application.properties`의 qa_pilsa (원격 MySQL). Flyway/Liquibase **없음** — DDL은 DB에 수동 적용하며, **.sql 파일을 레포에 커밋하지 않는 것이 팀 컨벤션** (PR #66/#68 PM 결정).
- Redis: 리프레시 토큰 저장에 사용(localhost:6379). 로그인/토큰 API 테스트 시 필요.
- Swagger: `/swagger-ui/index.html`.

## 패키지 구조 (도메인별 controller/dto/exception/mapper/service)
```
com.back
├── auth          # 로그인/회원가입/토큰 (AuthServicempl — 오타지만 유지)
├── board         # 재학생 게시판 통합 (1=공지, 2=자유, 3=정보) — BoardType enum이 게시판별 정책
├── admin
│   ├── common    # AdminServiceSupport(관리자 id/페이지 보정), BulkResultResponse(부분 성공)
│   ├── moderation# 게시글/댓글 공통 조치(blind/restore/softDelete) + moderation_log/penalty_log
│   ├── post      # 관리자 게시글 관리
│   ├── report    # 관리자 신고 관리 (대상 단위 반려/삭제, 일괄)
│   └── sanction  # 제재 현황/해제 + 주의→경고→정지 에스컬레이션(PenaltyEscalationService)
├── report        # 학생 신고 접수 (POST /api/stu/reports)
├── member        # 관리자 회원 관리 (목록/수정/정지/영구차단)
├── event         # 일정 캘린더 (구 schedule)
├── aboutPilsa    # 명예의전당(Honor)
├── student.common# FileStorageUtil (파일 저장)
└── global        # config(Security/Cors/Web/OpenApi/Async), security(JWT), mail, role, exception
```
매퍼 XML: `src/main/resources/mapper/<도메인>/*.xml` (mybatis mapper-locations로 자동 스캔).

## 핵심 도메인 규칙
- **권한 2축**: `users.member_type`(STUDENT/ALUMNI) + `users.admin_level`(0=일반, 1~3=관리자).
  JWT 필터가 DB 최신값 기준으로 ROLE_STUDENT/ROLE_ALUMNI (+ admin_level≥1이면 ROLE_ADMIN) 부여.
  URL: `/api/admin/**`=ADMIN, `/api/stu/**`=STUDENT·ALUMNI, `/api/public/**`=공개.
- **콘텐츠 상태**: posts/comments의 `state` = normal/blind/deleted. 학생 화면은 `state='normal'`만.
  모든 삭제는 소프트삭제. 관리자 조치는 반드시 `ModerationService` 경유(로그+벌점 일관성).
- **제재 파이프라인**: 관리자 삭제 → penalty +2 (`caution_per_delete`) → 유효합 10점당 경고 1회
  (`cautions_per_warning`) → 경고 횟수별 ban_policy(1주/1달/영구). 수치는 policy_settings에서 로드.
  users.ban_status/banned_until은 캐시 — 판정은 항상 banned_until 실시간 비교, 스케줄러(일 1회)는 캐시 정리만.
- **신고**: 접수는 report 패키지, 처리(반려/삭제)는 admin.report — 대상 단위로 pending 전부 일괄 종료
  (동일 대상 중복 신고로 벌점이 이중 부과되지 않도록 하는 장치이므로 유지할 것).

## 주의사항
- `users.role`/`status` 컬럼은 **DB에서 제거됨** — 코드에서 참조하면 런타임 SQL 오류.
- boards.`code`(NOTICE/FREE/INFO)는 관리자 화면 응답에 사용 중 — name으로 rename 금지(보류 결정).
- MyBatis 파라미터 2개 이상이면 @Param 필수. 날짜는 문자열 'YYYY-MM-DD'로 받는 API 다수.
- 예외는 도메인별 XxxException(BaseException 상속) → GlobalExceptionHandler가 {message} JSON으로 변환.
- 통합 이력·미해결 결정사항: `docs/integration-20260814/CHECKLIST.md` 참조.
