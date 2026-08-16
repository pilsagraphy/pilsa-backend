# 스웨거 전수 테스트 시나리오 (2026-08-16 작성)

> 대상: `api_endpoints` 의 **status='active' 69건**. planned 18건은 미구현이라 대상 아님.
> 도구: `http://localhost:8080/swagger-ui/index.html` (도메인별 태그 + 상단 filter 검색 가능)

## 0. 진행 방식 — confirmed_at

`api_endpoints` 에 **`confirmed_at` (date)** 컬럼을 추가했다. 테스트가 성공할 때마다 직접 채운다.

```sql
-- 한 건 확정
UPDATE api_endpoints SET confirmed_at = CURDATE()
WHERE http_method='POST' AND path='/api/auth/login';

-- 여러 건 한 번에
UPDATE api_endpoints SET confirmed_at = CURDATE() WHERE endpoint_id IN (127,128,77);

-- 진행 현황 보기
SELECT COUNT(*) AS 남은건수 FROM api_endpoints
WHERE status='active' AND (confirmed_at IS NULL OR confirmed_at <> CURDATE());

-- 도메인별 진행률
SELECT domain,
       SUM(confirmed_at = CURDATE()) AS 확정,
       COUNT(*) AS 전체
FROM api_endpoints WHERE status='active' GROUP BY domain ORDER BY domain;
```
판정 규칙: **`confirmed_at` 이 NULL 이거나 오늘 날짜가 아니면 미확인.**

---

## 1. 테스트 계정 (2026-08-16 시드 완료 — **비밀번호는 전부 wm5256 과 동일**)

| loginId | 상황 | 확인 용도 |
|---|---|---|
| `t_stu` | 재학생 · 일반 | 회원 API 주 계정 |
| `t_stu2` | 재학생 · 일반 | 상대역 — 댓글 달아 알림 발생, 신고 대상 글 작성 |
| `t_alu` | 졸업생 · 일반 | 신분 분기 (STUDENT 전용 게시판 만들면 403 확인) |
| `t_adm1` | 관리자 Lv1 | 공지(write_level=1) 작성 가능 / Lv2·3 게시판 불가 확인 |
| `t_adm2` | 관리자 Lv2 | write_level 경계 확인 |
| `t_adm3` | 관리자 Lv3 | 관리자 API 전체 |
| `t_susp` | 정지 중 (~2026-09-30) | 로그인 → **403 + banType:temporary + bannedUntil** |
| `t_ban` | 영구차단 | 로그인 → **403 + banType:permanent + bannedUntil:null** |
| `t_exp` | 정지 만료 (banned_until 과거) | 로그인 **성공**해야 정상 (판정은 실시간 비교) |
| `t_del` | 탈퇴 (is_deleted=1) | 로그인 거부 확인 |

※ 기존 test_* 계정(74~80)은 벌점·신고 이력 시나리오용으로 그대로 있음 (비밀번호 별도).

---

## 2. 전수 체크리스트 — active 69건, 쉬운 순서 (성공 시 confirmed_at 직접 입력)

```sql
-- 성공 처리 (id 는 아래 표의 id 컬럼)
UPDATE api_endpoints SET confirmed_at = CURDATE() WHERE endpoint_id IN (…);
```

### STEP 1. 공개 조회 (비로그인) — 4건
| # | id | API | 제목 | ✔ |
|---|----|-----|------|---|
| 1 | 133 | `GET /api/donations` | 명예의전당 페이지 - 후원자 전체 목록 | ☐ |
| 2 | 69 | `GET /api/event` | 일정(캘린더) 페이지 - 기간별 일정 목록 조회 | ☐ |
| 3 | 67 | `GET /api/event/calendar.ics` | 일정(캘린더) 페이지 - 구글 캘린더 구독 피드 (ICS) | ☐ |
| 4 | 31 | `GET /api/quotes/current` | 메인페이지 이주의문장 - 이 주의 문장 (노출기간 내 랜덤 1건) | ☐ |

### STEP 2. 이메일 인증번호 — 3건
| # | id | API | 제목 | ✔ |
|---|----|-----|------|---|
| 5 | 131 | `POST /api/mail/verification-code` | 이메일 인증번호 발송 | ☐ |
| 6 | 87 | `GET /api/mail/verification-code/ttl` | 이메일인증 - 인증번호 남은 유효시간 조회 (타이머) | ☐ |
| 7 | 132 | `POST /api/mail/verification-code/verify` | 이메일 인증번호 검증 | ☐ |

### STEP 3. 인증 (회원가입→로그인→계정찾기→토큰) — 12건
| # | id | API | 제목 | ✔ |
|---|----|-----|------|---|
| 8 | 77 | `GET /api/auth/check` | 회원가입 페이지 - 아이디/이메일 중복 확인 | ☐ |
| 9 | 32 | `POST /api/auth/email/find` | 이메일찾기 페이지 - 이메일 찾기 (학번+이름, 마스킹 반환) | ☐ |
| 10 | 82 | `GET /api/auth/id/find` | 아이디찾기 페이지 - 인증 완료된 이메일로 아이디 조회 | ☐ |
| 11 | 81 | `POST /api/auth/id/verify` | 아이디찾기 페이지 - 이메일+인증번호 검증 | ☐ |
| 12 | 127 | `POST /api/auth/login` | 로그인 페이지 - 로그인 (accessToken 반환 + refreshToken 쿠키) | ☐ |
| 13 | 84 | `PUT /api/auth/password/reset` | 비밀번호찾기 페이지 - 비밀번호 초기화 | ☐ |
| 14 | 128 | `POST /api/auth/register` | 회원가입 페이지 - 회원가입 | ☐ |
| 15 | 130 | `POST /api/auth/token/access/refresh` | 액세스 토큰 발급/재발급 (+ 리프레시 토큰 회전) | ☐ |
| 16 | 75 | `POST /api/auth/token/logout` | 로그인/로그아웃 - 로그아웃 (리프레시 토큰 삭제) | ☐ |
| 17 | 129 | `POST /api/auth/token/refresh/extend` | 리프레시 토큰 연장(재발급) - 로그인 유지 수동 연장 | ☐ |
| 18 | 78 | `POST /api/auth/token/refresh/validate` | 로그인/로그아웃 - 리프레시 토큰 쿠키 존재 확인 | ☐ |
| 19 | 83 | `GET /api/auth/verification` | 비밀번호찾기 페이지 - 아이디+이메일 검증 후 인증번호 발송 | ☐ |

### STEP 4. 회원 조회 (t_stu 토큰) — 10건
| # | id | API | 제목 | ✔ |
|---|----|-----|------|---|
| 20 | 134 | `GET /api/role` | 로그인 사용자 신분·관리권한 조회 | ☐ |
| 21 | 1 | `GET /api/user/boards` | 사이드바 게시판 목록 - 현재 로그인한 사람이 열람 가능한 게시판 목록 | ☐ |
| 22 | 3 | `GET /api/user/boards/{boardId}/categories` | 공통게시판 페이지 - 게시판 카테고리 목록 | ☐ |
| 23 | 4 | `GET /api/user/boards/{boardId}/posts` | 공통게시판 페이지 - 게시글 목록 (페이징/검색/정렬/카테고리) | ☐ |
| 24 | 14 | `GET /api/user/boards/{boardId}/posts/top/{num}` | 공통게시판 페이지 - 상단 N개 (is_pinned 우선) | ☐ |
| 25 | 6 | `GET /api/user/boards/{boardId}/posts/{postId}` | 공통게시판 페이지 - 게시글 상세 (첨부·좋아요·이전다음). 댓글은 별도 API | ☐ |
| 26 | 123 | `GET /api/user/boards/{boardId}/posts/{postId}/comments` | 공통게시판 페이지 - 댓글/대댓글 목록 | ☐ |
| 27 | 25 | `GET /api/user/mypage/toast` | 알림 목록 (unreadOnly 필터) | ☐ |
| 28 | 29 | `GET /api/user/mypage/toast/unread-count` | 미읽음 알림 수 (뱃지) | ☐ |
| 29 | 138 | `GET /api/user/mypage/toast/vapid-key` | 알림 발송 서버 공개키 (VAPID) | ☐ |

### STEP 5. 회원 쓰기 (게시글·댓글·신고·알림·기기) — 13건
| # | id | API | 제목 | ✔ |
|---|----|-----|------|---|
| 30 | 5 | `POST /api/user/boards/{boardId}/posts` | 게시글 등록 (multipart) | ☐ |
| 31 | 7 | `PUT /api/user/boards/{boardId}/posts/{postId}` | 게시글 수정 (작성자/관리자) | ☐ |
| 32 | 9 | `POST /api/user/boards/{boardId}/posts/{postId}/comments` | 공통게시판 페이지 - 댓글/대댓글 등록 | ☐ |
| 33 | 10 | `PUT /api/user/boards/{boardId}/posts/{postId}/comments/{commentId}` | 공통게시판 페이지 - 댓글/대댓글 수정 | ☐ |
| 34 | 11 | `PATCH /api/user/boards/{boardId}/posts/{postId}/comments/{commentId}/delete` | 공통게시판 페이지 - 댓글/대댓글 삭제 (소프트, 본인만) | ☐ |
| 35 | 8 | `PATCH /api/user/boards/{boardId}/posts/{postId}/delete` | 공통게시판 페이지 - 게시글 삭제 (소프트, 작성자 본인만) | ☐ |
| 36 | 12 | `PATCH /api/user/boards/{boardId}/posts/{postId}/like` | 공통게시판 페이지 - 좋아요 토글 | ☐ |
| 37 | 136 | `POST /api/user/mypage/toast/devices` | 알림 수신 기기 등록 (웹 푸시) | ☐ |
| 38 | 137 | `DELETE /api/user/mypage/toast/devices` | 알림 수신 기기 해제 | ☐ |
| 39 | 28 | `PATCH /api/user/mypage/toast/read-all` | 알림 전체 읽음 처리 | ☐ |
| 40 | 26 | `PATCH /api/user/mypage/toast/{toastId}/delete` | 알림 삭제 (소프트) | ☐ |
| 41 | 27 | `PATCH /api/user/mypage/toast/{toastId}/read` | 알림 읽음 처리 (단건) | ☐ |
| 42 | 20 | `POST /api/user/reports` | 공통게시판/댓글 신고 페이지 - 게시글/댓글 신고 접수 | ☐ |

### STEP 6. 관리자 조회 (t_adm3 토큰) — 11건
| # | id | API | 제목 | ✔ |
|---|----|-----|------|---|
| 43 | 34 | `GET /api/admin/boards` | 게시판관리 페이지 - 게시판 목록 (게시글 수·권한 설정 포함) | ☐ |
| 44 | 38 | `GET /api/admin/posts` | 게시글관리 페이지 - 게시글 목록 (전 게시판, 검색) | ☐ |
| 45 | 39 | `GET /api/admin/posts/{postId}` | 게시글관리 페이지 - 게시글 상세 (blind/deleted 열람, 실작성자) | ☐ |
| 46 | 63 | `GET /api/admin/quotes` | 문장 목록 | ☐ |
| 47 | 48 | `GET /api/admin/reports/comments` | 신고관리페이지 - 신고된 댓글 목록 (원글 postId 포함) | ☐ |
| 48 | 49 | `GET /api/admin/reports/posts` | 신고관리페이지 - 신고된 게시글 목록 (대상별 그룹핑) | ☐ |
| 49 | 55 | `GET /api/admin/sanctions/users` | 제재회원목록 페이지 - 제재 회원 목록 (permanent/temporary/caution 태그) | ☐ |
| 50 | 56 | `GET /api/admin/sanctions/users/{userId}` | 제재회원목록 페이지 - 제재 회원 상세 (누적주의/경고/신고삭제수) | ☐ |
| 51 | 126 | `GET /api/admin/sanctions/users/{userId}/reports/comments` | 제재회원목록 페이지 - 회원별 신고된 댓글 내역 | ☐ |
| 52 | 58 | `GET /api/admin/sanctions/users/{userId}/reports/posts` | 제재회원목록 페이지 - 회원별 신고된 게시글 내역 | ☐ |
| 53 | 59 | `GET /api/admin/users` | 회원목록 페이지 - 회원 목록 (검색/정렬/페이징/활동수/정지기간) | ☐ |

### STEP 7. 관리자 쓰기 (게시판·회원·신고조치·문장·일정) — 16건
| # | id | API | 제목 | ✔ |
|---|----|-----|------|---|
| 54 | 35 | `POST /api/admin/boards` | 게시판관리 페이지 - 새 게시판 생성 (read_scope/write_level 등) | ☐ |
| 55 | 36 | `PATCH /api/admin/boards/{boardId}` | 게시판관리 페이지 - 게시판 수정 (전달 필드만) | ☐ |
| 56 | 37 | `PATCH /api/admin/boards/{boardId}/delete` | 게시판관리 페이지 - 게시판 삭제 (소프트, 글 있으면 409) | ☐ |
| 57 | 68 | `POST /api/admin/event` | (관리자) 일정(캘린더) 페이지 - 일정 등록 | ☐ |
| 58 | 71 | `PUT /api/admin/event/{eventId}` | (관리자) 일정(캘린더) 페이지 - 일정 수정 (부분 수정) | ☐ |
| 59 | 122 | `DELETE /api/admin/event/{eventId}` | (관리자) 일정(캘린더) 페이지 - 일정 삭제 (소프트) | ☐ |
| 60 | 64 | `POST /api/admin/quotes` | 문장 등록 (노출기간) | ☐ |
| 61 | 65 | `PUT /api/admin/quotes/{quoteId}` | 문장 수정 | ☐ |
| 62 | 66 | `PATCH /api/admin/quotes/{quoteId}/delete` | 문장 삭제 (소프트) | ☐ |
| 63 | 52 | `PATCH /api/admin/reports/select-blind` | 신고/게시글/댓글관리페이지 - 신고 선택 블라인드 (일괄) | ☐ |
| 64 | 51 | `PATCH /api/admin/reports/select-delete` | 신고/게시글/댓글관리페이지 - 신고 선택 삭제 (일괄) | ☐ |
| 65 | 50 | `PATCH /api/admin/reports/select-restore` | 신고관리페이지 - 신고 선택 복원 (일괄) | ☐ |
| 66 | 57 | `POST /api/admin/sanctions/users/{userId}/lift` | (3기 진행 예정) 제재 수동 해제 | ☐ |
| 67 | 62 | `PATCH /api/admin/users/ban` | 회원목록 페이지 - 회원 영구차단 (단일/다중) | ☐ |
| 68 | 60 | `PATCH /api/admin/users/{userId}` | 회원목록 페이지 - 회원 정보 수정 (부분) | ☐ |
| 69 | 61 | `PATCH /api/admin/users/{userId}/suspend` | 회원목록 페이지 - 회원 정지 (temporary) | ☐ |

---

## 2-1. 상세 시나리오 (위 표를 진행할 때 참고)

### STEP 1. 회원가입·계정찾기 (인증 도메인 15건)

| # | API | 요청 예시 | 기대 |
|---|---|---|---|
| 1 | `POST /api/mail/verification-code` | `{"email":"본인메일@test.com"}` | `{message, expireTime:300}` — **실제 메일 수신 확인** |
| 2 | `GET /api/mail/verification-code/ttl` | `?email=본인메일@test.com` | 남은 초(Long 단독 — 1기 형식) |
| 3 | `POST /api/mail/verification-code/verify` | `{"email":"...","code":"메일의 6자리"}` | `{message, verified:true}` / 틀리면 400 |
| 4 | `GET /api/auth/check` | `?loginId=testuser01` | 200 빈 본문(사용 가능) → 이미 있으면 400 문자열 |
| 5 | `POST /api/auth/register` | 아래 §3-A | `{"message":"회원가입이 완료되었습니다."}` |
| 6 | `POST /api/auth/login` | `{"loginId":"testuser01","password":"Test1234!"}` | `{accessToken, userId, memberType:"STUDENT", adminLevel:0, refreshExp}` |
| 7 | `POST /api/auth/login` (정지) | `{"loginId":"test_susp1w", ...}` | **403 + `banType`,`bannedUntil`** ← 이번에 고친 핵심 |
| 8 | `POST /api/auth/token/refresh/validate` | 본문 없음 | 200(쿠키 있음) / 204(없음) — 1기 형식 |
| 9 | `POST /api/auth/token/access/refresh` | 본문 없음(쿠키) | 새 accessToken, 쿠키도 회전 |
| 10 | `POST /api/auth/token/refresh/extend` | 본문 없음(쿠키) | 새 accessToken |
| 11 | `POST /api/auth/email/find` | `{"studentNo":"20201234","name":"테스트유저"}` | `{message, email:"te****@test.com"}` 마스킹 |
| 12 | `POST /api/auth/id/verify` | `{"email":"...","code":"..."}` | 인증번호 재발송(#1) 후 검증 |
| 13 | `GET /api/auth/id/find` | `?email=본인메일@test.com` | `{message, loginId}` — #12 통과 후에만 |
| 14 | `GET /api/auth/verification` | `?loginId=testuser01&email=본인메일@test.com` | `{message, expireTime}` + 메일 발송 |
| 15 | `PUT /api/auth/password/reset` | `{"loginId":"testuser01","newPassword":"NewPass1234!"}` | 200 빈 본문 → **바뀐 비번으로 재로그인 확인** |
| 16 | `POST /api/auth/token/logout` | 본문 없음 | 200 빈 본문(쿠키 삭제) |

> ⚠️ 마이페이지 비밀번호 변경(`PATCH /api/user/mypage/password/reset`)은 **planned(미구현)** 이라 이번 대상 아님.

### STEP 2. 공개 API (로그인 없이, 4건)
`GET /api/donations` / `GET /api/quotes/current` / `GET /api/event?from=2026-01&to=2026-12` / `GET /api/event/calendar.ics`
→ 마지막 것은 브라우저 새 탭으로 열어 `BEGIN:VCALENDAR` 텍스트 확인.

### STEP 3. 로그인 회원 기본 (일반 계정 `purpletree0210` 토큰)

1. `GET /api/role` → `{memberType, adminLevel}`
2. `GET /api/user/boards` → 열람 가능 게시판 목록(canWrite 포함)
3. `GET /api/user/boards/2/categories` → 자랑/정보/질문/일상/모임 (**'중요' 안 보이면 정상**)
4. `GET /api/user/boards/2/posts?page=1&size=10` → 목록(created만 있고 updated 없음 확인)
5. `GET /api/user/boards/2/posts/top/3` → **3건만** 오는지 (top/50 초과 시 400도 확인)
6. `GET /api/user/boards/2/posts/{postId}` → 상세(**commentCount만 있고 comments 배열 없음**), 조회수 +1
7. `GET /api/user/boards/2/posts/{postId}/comments` → 댓글 목록(별도 API)
8. `GET /api/user/mypage/toast` / `/unread-count` → 알림함·뱃지

### STEP 4. 게시판 쓰기 (핵심 — 이번 변경 집중)

| 순서 | API | 확인 포인트 |
|---|---|---|
| 1 | `POST /api/user/boards/2/posts` (multipart) | 응답에 **`postId` 포함**(신규) / 파일 2개 첨부 |
| 2 | 첨부 확인 | 상세의 `fileUrl` 을 새 탭으로 열기 → **원본 파일명으로 저장되는지**(UUID 아님) |
| 3 | `POST` 검증 실패 | title 빈값 → **400 "제목은 필수입니다."** (500 아님) |
| 4 | `PUT .../posts/{postId}` (multipart) | 응답이 **`{message}` 만**(상세 객체 아님) |
| 5 | 첨부 증분 | `deleteAttachmentIds=[기존id]` + 새 `files` → 지운 건 사라지고 새 건 추가 |
| 6 | `PUT` 검증 실패 | content 빈 문자열 → **400**(예전엔 빈 값이 그대로 저장됐음) |
| 7 | `POST .../comments` | 댓글 등록 → 다른 계정으로 로그인해 **알림 도착 확인**(`GET /api/user/mypage/toast`) |
| 8 | 대댓글 | `parentCommentId` 지정 → 부모 댓글 작성자에게 알림 |
| 9 | `PATCH .../posts/{postId}/like` | 토글 2회 → "좋아요를 눌렀습니다/취소했습니다" |
| 10 | `PATCH .../posts/{postId}/delete` | **PATCH** 경로(DELETE 아님) — 소프트삭제 |
| 11 | 공지 쓰기 차단 | 일반 계정으로 `POST /api/user/boards/1/posts` → **403** (공지 write_level=1) |
| 12 | `POST /api/user/reports` | 다른 사람 글 신고 → 성공 / 같은 글 재신고 → **409** / 본인 글 → **400** |

### STEP 5. 알림 기기 등록 (신규 3건)
1. `GET /api/user/mypage/toast/vapid-key` → publicKey 확인
2. `POST /api/user/mypage/toast/devices` → 아래 §3-D 더미로 등록 → `{message}` (실제 발송은 브라우저 필요)
3. `DELETE /api/user/mypage/toast/devices` → `{"endpoint":"같은 값"}` → 해제

### STEP 6. 관리자 API (관리자 계정으로 Authorize 교체)

1. **회원**: `GET /api/admin/users` → `PATCH /api/admin/users/{userId}` → `PATCH .../suspend`(정지) → `PATCH /api/admin/users/ban`
   ⚠️ 정지/차단은 **테스트 계정(test_*)에만** 적용할 것
2. **게시판**: `GET /api/admin/boards` → `POST`(생성) → `PATCH /{boardId}`(수정) → `PATCH /{boardId}/delete`(삭제)
   - 생성한 게시판이 즉시 `GET /api/user/boards` 에 뜨는지 확인 (데이터 기반 동적 게시판)
   - `readScope:"ALL"` 넣어보기 → **400** (ALL 폐지 확인)
   - 글 있는 게시판 삭제 → **409**
3. **게시글**: `GET /api/admin/posts` → `GET /api/admin/posts/{postId}` (블라인드 글도 열람, 익명글 실작성자 노출)
4. **신고**: `GET /api/admin/reports/posts` / `comments` → 선택 조치 3종
   - `PATCH /api/admin/reports/select-blind` → 대상이 학생 화면에서 사라지는지
   - `PATCH /api/admin/reports/select-restore` → 복원 + 신고 rejected
   - `PATCH /api/admin/reports/select-delete` → 삭제 + **작성자 주의 +2**
   - 없는 id 섞어 보내기 → `{successCount:1, failCount:1, failures:[...]}` **부분 성공** 확인
5. **제재**: `GET /api/admin/sanctions/users` → `/{userId}` → `/{userId}/reports/posts` · `/reports/comments` → `POST /{userId}/lift`
6. **문장**: `GET /api/admin/quotes` → `POST` → `PUT /{quoteId}` → `PATCH /{quoteId}/delete`
   - 등록 후 `GET /api/quotes/current` 에 노출되는지(노출기간 내)
7. **일정**: `POST /api/admin/event` → `PUT /{eventId}` → `DELETE /{eventId}`
   - 등록 후 `GET /api/event` 와 `calendar.ics` 에 반영되는지

---

## 3. 예시 데이터 (복붙용)

### A. 회원가입
```json
{
  "name": "테스트유저",
  "phone": "010-0000-0001",
  "major": "컴퓨터공학과",
  "studentNo": "20201234",
  "email": "본인메일@test.com",
  "loginId": "testuser01",
  "password": "Test1234!",
  "memberType": "STUDENT"
}
```
※ `memberType` 에 `"ADMIN"` 을 넣어보면 400 이어야 정상(가입으로 관리자 승격 불가).

### B. 게시글 등록 (multipart — 스웨거 폼에 각 필드 입력)
```
title:        테스트 게시글입니다
content:      ## 마크다운 본문\n- 항목 1\n- 항목 2
categoryId:   4          (자유게시판 '일상')
isAnonymous:  false
files:        아무 이미지/PDF 2개 (한글 파일명으로 하나 넣어볼 것)
```
검증 실패 케이스: `title` 을 공백으로 → 400 / `title` 201자 → 400 / `content` 공백 → 400

### C. 댓글·대댓글·신고
```json
// 댓글
{"content":"테스트 댓글입니다","isAnonymous":false,"isPrivate":false}
// 대댓글 (parentCommentId 는 위에서 받은 commentId)
{"content":"테스트 대댓글","parentCommentId":200,"isAnonymous":false,"isPrivate":false}
// 신고
{"targetType":"post","targetId":171,"reasonId":2,"detail":null}
// 기타 사유일 때만 detail 작성
{"targetType":"comment","targetId":200,"reasonId":8,"detail":"기타 사유 설명"}
```

### D. 알림 기기 등록 (실제 브라우저 없이 형식만 검증할 때)
```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/TEST-DUMMY-0001",
  "keys": {
    "p256dh": "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM",
    "auth": "tBHItJI5svbpez7KI4CCXg"
  }
}
```
※ 더미라 실제 푸시는 안 가고, 등록/해제 API 동작만 확인용. 해제는 같은 endpoint 로.

### E. 관리자 — 게시판 생성 / 신고 조치 / 문장 / 일정
```json
// 게시판 생성
{"name":"테스트 게시판","readScope":"MEMBER","writeLevel":0,
 "allowComment":true,"allowAttachment":true,"categoryMode":false,
 "allowAnonymous":false,"allowPrivateComment":false}

// 게시판 수정 (전달 필드만)
{"name":"테스트 게시판(수정)","readScope":"STUDENT"}

// 신고 선택 조치 3종 공통 — 없는 id 를 섞어 부분 성공 확인
{"targetType":"post","targetIds":[171,999999],"reasonId":2,"detail":null}
{"targetType":"post","targetIds":[171]}                       // select-restore 는 사유 없음

// 회원 정지 (테스트 계정에만!)
{"endDate":"2026-09-30"}
// 회원 영구차단
{"userIds":[75]}

// 문장 등록
{"content":"테스트 문장입니다.","startDate":"2026-08-16","endDate":"2026-12-31"}

// 일정 등록
{"title":"테스트 일정","category":"정기모임","description":"테스트 설명",
 "startDate":"2026-09-01","endDate":"2026-09-02"}
```

---

## 4. 테스트 중 특히 볼 것 (이번에 바뀐 것들)

- [ ] 로그인 실패/정지 응답이 **JSON**이고 정지 시 `banType`·`bannedUntil` 포함
- [ ] 게시글 등록 응답에 **postId**
- [ ] 게시글 수정 응답이 **{message}만**
- [ ] 수정 시 title/content 빈 값 → **400** (예전엔 그대로 저장)
- [ ] 첨부가 **원본 파일명**으로 저장·다운로드 (한글 파일명 포함)
- [ ] 첨부 삭제 후 `uploads/board-2/{postId}/` 폴더에서 **실제 파일도 사라짐**
- [ ] 상세에 comments 없음 + **댓글 별도 API** 동작
- [ ] `posts/top/{num}` 이 요청 개수만큼
- [ ] 관리자 조치가 **select-* 3종**으로만 되고 부분 성공 응답
- [ ] 관리자 수정/정지/차단/삭제가 **PATCH**
- [ ] 게시판 생성 시 `readScope:"ALL"` → 400
- [ ] 댓글/대댓글 시 **상대방에게만** 알림(본인 행동은 알림 없음)

## 5. 실패 시 기록
`confirmed_at` 을 채우지 말고, 실패 내용을 이 문서 아래에 추가하거나 바로 알려줄 것.
(요청/응답 전문 + 서버 로그가 있으면 원인 파악이 빠름)
