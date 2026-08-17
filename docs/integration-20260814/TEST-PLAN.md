# 스웨거 전수 테스트 시나리오 (2026-08-16 작성)

> 대상: `api_endpoints` 의 **status='active' 66건**. planned 18건은 미구현이라 대상 아님.
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

※ 기존 test_* 계정(74~80)은 **로그인하지 않는다** — 관리자 화면(제재·신고 목록)에 표시될 이력 데이터 픽스처. 역할은 §2-1 참고.

### Play Console 심사팀 제공 계정 (테스트용 아님 — 건드리지 말 것)

| 용도 | 아이디 | 비밀번호 | 권한 |
|---|---|---|---|
| 일반 회원 화면 | `testtest` | `test1234!` | 재학생 · admin_level 0 · user_id 111 |
| 관리자 화면 | `testadmin` | `test1234!` | 재학생 · **admin_level 1** · user_id 112 |

Play Console → **앱 콘텐츠 → 앱 액세스**에 두 계정을 각각 등록한다(항목 여러 개 추가 가능).
앱 전체가 로그인 뒤에 있어 심사팀이 로그인하지 못하면 "콘텐츠 확인 불가"로 반려되며,
관리자 화면이 앱에 포함되므로 그쪽도 자격증명을 줘야 한다.

- 관리자 계정을 **Lv1** 로 둔 이유: 모든 `/api/admin/**` 이 `requireAdmin()` 이라 Lv1 도 관리 화면을
  전부 볼 수 있고, Lv3 과의 차이는 write_level 2~3 게시판 작성뿐이라 심사 커버리지에 지장이 없다.
- **강제 탈퇴(id=140)만 Lv3 전용**이라 심사관이 실수로 회원을 파기할 수 없다 (Lv1 호출 시 403 확인 완료).
- **심사 기간 내내 살아 있어야 하므로 탈퇴·비밀번호 변경·제재 대상으로 쓰지 말 것.**
- 2026-08-16 실제 회원가입 API(인증번호 발송 → 검증 → register)로 생성. 관리 권한(admin_level)만
  DB 로 부여 — 권한 승격은 관리자 토큰이 필요한 API 라 별도 경로가 없다.

---

## 2. 전수 체크리스트 — active 66건, 쉬운 순서 (성공 시 confirmed_at 직접 입력)

> 순번에 빠진 숫자가 있는 것은 정상입니다 — 알림함 API 5종(id 25·26·27·28·29)이
> 2026-08-16 담당자 과제로 환원되어 테스트 대상에서 제외됐습니다. (`HANDOFF-notification-tasks.md`)

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
| 42 | 20 | `POST /api/user/reports` | 공통게시판/댓글 신고 페이지 - 게시글/댓글 신고 접수 | ☐ |
| 42-1 | 139 | `PATCH /api/user/mypage/withdraw` | 회원 탈퇴 (개인정보 파기 — **맨 마지막에, 버릴 계정으로**) | ☐ |
| 42-2 | 140 | `PATCH /api/admin/users/{userId}/withdraw` | 회원 강제 탈퇴 (관리자 — 관리자 계정 대상 시 400 확인) | ☐ |

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

## 2-1. 상세 시나리오 — API 별 입력과 기대 출력

> 아래 입력/기대 출력은 명세 정본(api_endpoints)의 request_example / response_example 그대로다.
> **이대로 넣었는데 이대로 안 나오면 실패**이고, 실패 응답(400/403/404/409)도 명시된 형태로 나와야 한다.

### 경로변수에 넣을 값

| 변수 | 값 | 비고 |
|---|---|---|
| `{boardId}` | `2` | 자유게시판 (익명·댓글·첨부·카테고리 전부 허용) |
| `{postId}` | STEP 5-①(게시글 등록) 응답의 `postId` | 조회 계열은 기존 글 id 아무거나 가능 |
| `{commentId}` | STEP 5(댓글 등록) 후 댓글 목록에서 확인 | |
| `{num}` | `3` | 상단 N개 — 3건만 오는지 |
| `{toastId}` | (알림함 API 미구현 — 담당자 과제) | |
| `{userId}` | 정지=**75**(test_susp1w), 차단=**77**(test_banned), 상세조회=**79**(test_reported_author) | 제재 화면용 |
| `{quoteId}` | STEP 7(문장 등록) 응답의 id | |
| `{eventId}` | STEP 7(일정 등록) 응답의 `eventId` | |
| `categoryId` | `4` (자유게시판 '일상') | 관리자 토큰이면 `12`('중요') 선택 시 상단 고정 |
| `reasonId` | `2` (욕설·비방) / 기타는 `8` + detail 필수 | |

### 기존 test_* 계정(74~80)의 역할 — 로그인하지 않는다 (비밀번호 별도)

이들은 **관리자 화면에 표시될 이력 데이터가 미리 걸려 있는 픽스처**다. 지우면 STEP 6 화면들이 빈 목록이 된다.

| 계정 | 걸려 있는 데이터 | 어느 테스트에서 보이나 |
|---|---|---|
| test_caution(74) | 주의 1건 + 글 2건 | 제재 목록의 caution 태그 |
| test_susp1w(75) / test_susp1m(76) | 주의+경고+ban_log (1주/1달 정지) | 제재 목록 temporary / 제재 해제(lift) 대상 |
| test_banned(77) | 경고 3회 + 영구 ban_log | 제재 목록 permanent |
| test_expired(78) | 만료된 정지 이력 | 제재 목록에서 빠지는지(만료 판정) |
| test_reported_author(79) | 글 3·댓글 1 + 신고당한 이력 | 회원별 신고 내역(/reports/posts·comments) |
| test_reporter(80) | 신고 4건 (pending 3 / rejected 1 / resolved 1) | 신고 관리 목록 + select-* 조치 대상 |

### STEP 1. 공개 조회 (비로그인) — 계정: 없음 (Authorize 해제 상태로)

#### 1) `GET /api/donations` — 명예의전당 페이지 - 후원자 전체 목록  (id 133)
**입력**
```
없음 (쿼리 없음)
```
**기대 출력**
```
[
  {"donationId":1,"amount":100000,"displayName":"홍길동","affiliation":"13기",
   "major":"컴퓨터공학과","message":"응원합니다","donatedAt":"2026-02-20T10:00:00",
   "isAnonymous":false,"photoUrl":"/uploads/honor/uuid.png"}
]
```
> 비로그인 열람 가능(SecurityConfig permitAll). 익명 후원이면 displayName이 '익명후원자'로 치환되어 내려간다. 구 /api/public/honor/

#### 2) `GET /api/event` — 일정(캘린더) 페이지 - 기간별 일정 목록 조회  (id 69)
**입력**
```
쿼리: ?from=2026-03&to=2026-03  (from·to 둘 다 필수)

YYYY-MM / YYYY-MM-DD 둘 다 허용:
  7자리로 오면 from은 해당 월 1일, to는 해당 월 말일로 서버가 자동 변환
  (예: from=2026-02 -> 2026-02-01, to=2026-02 -> 2026-02-28)
```
**기대 출력**
```
{
  "message": "일정 목록을 성공적으로 불러왔습니다.",
  "data": [
    {"eventId":1,"title":"3월 정기모임","category":"정기모임",
     "description":"3월 정기모임 안내","startDate":"2026-03-01","endDate":"2026-03-01"}
  ]
}

실패: 400 {"message":"..."} (from 또는 to 누락 시)
```
> SecurityConfig permitAll (비로그인 열람 가능). state=normal만 조회. 기간이 걸치기만 하면 포함(start_at<=to AND end_at>=from), start_at 오름차순. 날짜는 DATE_FORMAT으로 YYYY-MM-DD만 반환(시각 미반환)

#### 3) `GET /api/event/calendar.ics` — 일정(캘린더) 페이지 - 구글 캘린더 구독 피드 (ICS)  (id 67)
**입력**
```
없음 (쿼리 없음)

프론트 [구독하기] 버튼:
window.open(
  'https://calendar.google.com/calendar/render?cid='
  + encodeURIComponent('https://{서비스 도메인}/api/event/calendar.ics')
)
```
**기대 출력**
```
Content-Type: text/calendar

BEGIN:VCALENDAR
VERSION:2.0
X-WR-CALNAME:필사그래피 일정
BEGIN:VEVENT
UID:pilsa-event-12@pilsagraphy
DTSTART;VALUE=DATE:20261020
DTEND;VALUE=DATE:20261022
SUMMARY:가을 MT
CATEGORIES:정기모임
DESCRIPTION:일시/장소/준비물 ...
END:VEVENT
END:VCALENDAR
```
> 한 번 구독하면 이후 등록/수정/삭제되는 모든 일정이 구독자 구글 캘린더에 자동 반영(구글이 수 시간~하루 주기로 재조회). OAuth·구글 API 불필요 — 표준 iCalendar 피드라 애플/아웃룩 캘린더도 같은 URL 로 구독 가능. 구글 서버가 인증 없이 가져가야 하므로 PUBLIC(SecurityConfig permitAll). state=normal 일정만 포함, 종일 일정(VALUE=DATE) 규격

#### 4) `GET /api/quotes/current` — 메인페이지 이주의문장 - 이 주의 문장 (노출기간 내 랜덤 1건)  (id 31)
**입력**
```
없음
```
**기대 출력**
```
{"content":"바다는 비에 젖지 않는다."}
※ 노출기간(start~end) 내 문장 중 랜덤 1건
```
> 구 /api/public/quotes/random

### STEP 2. 이메일 인증번호 — 계정: 없음 (비로그인)

#### 5) `POST /api/mail/verification-code` — 이메일 인증번호 발송  (id 131)
**입력**
```
{"email":"hong@pilsa.co.kr"}
```
**기대 출력**
```
{"message":"인증번호를 발송했습니다.","expireTime":300}

실패: 400 {"message":"이메일을 입력해주세요."}
     500 {"message":"인증번호 발송에 실패했습니다. 잠시 후 다시 시도해주세요."}
```
> 회원가입·아이디찾기 공용. 구 버전은 Long 원시값 반환 + 무본문 400/404였음

#### 6) `GET /api/mail/verification-code/ttl` — 이메일인증 - 인증번호 남은 유효시간 조회 (타이머)  (id 87)
**입력**
```
쿼리: ?email=hong@pilsa.co.kr (선택)
```
**기대 출력**
```
245  (남은 초, Long 단독 반환)
```
> 1기(2026-02~03) 개발

#### 7) `POST /api/mail/verification-code/verify` — 이메일 인증번호 검증  (id 132)
**입력**
```
{"email":"hong@pilsa.co.kr","code":"123456"}
```
**기대 출력**
```
{"message":"인증이 완료되었습니다.","verified":true}

실패: 400 {"message":"이메일과 인증번호를 모두 입력해주세요."}
     400 {"message":"인증번호가 일치하지 않거나 만료되었습니다."}
```
> 구 버전은 불일치도 200 + false 라서 프론트가 사유를 표시할 수 없었음 → 실패는 400 + message

### STEP 3. 인증 — 계정: 없음 (비로그인 — 로그인 후 발급된 토큰/쿠키는 이어서 사용)

#### 8) `GET /api/auth/check` — 회원가입 페이지 - 아이디/이메일 중복 확인  (id 77)
**입력**
```
쿼리: ?email=hong@pilsa.co.kr 또는 ?loginId=hong (둘 중 하나만)
```
**기대 출력**
```
200 (본문 없음, 사용 가능)

실패: 400 "이미 가입된 이메일 주소입니다." / "이미 사용 중인 아이디입니다."
```
> 1기(2026-02~03) 개발. 파라미터를 둘 다 안 주면 400

#### 9) `POST /api/auth/email/find` — 이메일찾기 페이지 - 이메일 찾기 (학번+이름, 마스킹 반환)  (id 32)
**입력**
```
{"studentNo":"2026010101","name":"홍길동"}
```
**기대 출력**
```
{"email":"ho**@pilsa.co.kr"}  // 마스킹된 이메일
```
> PR #67

#### 10) `GET /api/auth/id/find` — 아이디찾기 페이지 - 인증 완료된 이메일로 아이디 조회  (id 82)
**입력**
```
쿼리: ?email=hong@pilsa.co.kr
```
**기대 출력**
```
{"message":"아이디 조회 성공","loginId":"hong"}
```
> 1기(2026-02~03) 개발. 2기 POST /api/auth/email/find(이메일 찾기)와 방향이 반대인 API

#### 11) `POST /api/auth/id/verify` — 아이디찾기 페이지 - 이메일+인증번호 검증  (id 81)
**입력**
```
{"email":"hong@pilsa.co.kr","code":"123456"}
```
**기대 출력**
```
{"message":"이메일 인증이 완료되었습니다."}
```
> 1기(2026-02~03) 개발. 검증 통과 후에만 /api/auth/id/find 호출 가능

#### 12) `POST /api/auth/login` — 로그인 페이지 - 로그인 (accessToken 반환 + refreshToken 쿠키)  (id 127)
**입력**
```
{"loginId":"hong","password":"pw1234"}
```
**기대 출력**
```
{"accessToken":"eyJhbGciOi...","userId":80,"memberType":"STUDENT","adminLevel":0,"refreshExp":1740000000}

실패: 401 {"message":"아이디 또는 비밀번호가 올바르지 않습니다."}
     401 {"message":"승인되지 않은 계정입니다."}
정지: 403 {"message":"정지된 계정입니다.","banType":"temporary","bannedUntil":"2026-03-30T00:00:00"}
차단: 403 {"message":"영구적으로 차단된 계정입니다.","banType":"permanent","bannedUntil":null}
```
> 정지/차단 사유는 message로, 해제 일시는 bannedUntil 필드로 내려간다 — 프론트가 "2026.03.30 00:00 부터 다시 로그인 할 수 있습니다"를 그릴 수 있다. 실패 응답은 모두 JSON 객체(구 버전은 문자열이라 banType/bannedUntil이 유실됐음)

#### 13) `PUT /api/auth/password/reset` — 비밀번호찾기 페이지 - 비밀번호 초기화  (id 84)
**입력**
```
{"loginId":"hong","newPassword":"newpw1234"}
```
**기대 출력**
```
200 (본문 없음)
```
> 실패 케이스 추가(2026-08-16): 400 {"message":"비밀번호는 문자, 숫자, 특수문자를 포함한 8~20자여야 합니다."} — 회원가입과 동일 규칙
> 1기(2026-02~03) 개발. 로그인 상태의 비밀번호 변경(PATCH /api/user/mypage/password/reset)과 별개

#### 14) `POST /api/auth/register` — 회원가입 페이지 - 회원가입  (id 128)
**입력**
```
{"name":"홍길동","phone":"010-1234-5678","major":"컴퓨터공학과","studentNo":"2020123456",
 "email":"hong@pilsa.co.kr","loginId":"hong1234","password":"pw1234!@#","memberType":"STUDENT"}

※ memberType 미지정 시 STUDENT. ADMIN 등 임의 문자열은 400
```
**기대 출력**
```
{"message":"회원가입이 완료되었습니다."}

실패: 409 {"message":"이미 존재하는 아이디입니다."}
     409 {"message":"이미 존재하는 이메일입니다."} (학번/전화 중복도 409)
     400 {"message":"유효하지 않은 회원 구분입니다. (STUDENT/ALUMNI)"}
     400 형식 위반 — 이름 2자+한글/영문, 학번 숫자 10자리, 아이디 영숫자 8자+,
         비밀번호 문자·숫자·특수문자 8~20, 전화 010-0000-0000, 이메일 형식 (필드별 message)
     403 {"message":"이메일 인증이 완료되지 않았거나 만료되었습니다. 이메일 인증을 다시 진행해주세요."}
```
> 형식 검증 규칙은 프론트 zod(schemas/auth.js)와 동일하며 policy_settings(signup_*_regex)로 관리
> 관리 권한(admin_level)은 가입으로 못 얻는다 — 항상 0으로 저장되고 승격은 관리자만

#### 15) `POST /api/auth/token/access/refresh` — 액세스 토큰 발급/재발급 (+ 리프레시 토큰 회전)  (id 130)
**입력**
```
본문 없음 (refreshToken 쿠키)
```
**기대 출력**
```
{"accessToken":"eyJ...","userId":80,"memberType":"STUDENT","adminLevel":0,"refreshExp":1740000000}

실패: 401 {"message":"로그인 정보가 없습니다. 다시 로그인해주세요."}
정지/차단 계정은 403 + banType/bannedUntil
```
> 재발급 때마다 refreshToken 쿠키도 새로 교체된다(sliding). 매번 DB에서 회원 상태를 다시 확인하므로 정지된 계정은 즉시 막힌다

#### 16) `POST /api/auth/token/logout` — 로그인/로그아웃 - 로그아웃 (리프레시 토큰 삭제)  (id 75)
**입력**
```
본문 없음 (refreshToken 쿠키 사용)
```
**기대 출력**
```
200 (본문 없음)
```
> 1기(2026-02~03) 개발

#### 17) `POST /api/auth/token/refresh/extend` — 리프레시 토큰 연장(재발급) - 로그인 유지 수동 연장  (id 129)
**입력**
```
본문 없음 (refreshToken 쿠키)
```
**기대 출력**
```
{"accessToken":"eyJ...","userId":80,"memberType":"STUDENT","adminLevel":0,"refreshExp":1740000000}

실패: 401 {"message":"로그인 정보가 없습니다. 다시 로그인해주세요."}
     401 {"message":"Refresh token (로그인을 다시 해주세요.)"}
정지/차단 계정은 로그인과 동일하게 403 + banType/bannedUntil
```
> 구 버전은 쿠키가 없으면 무본문 401이라 프론트가 사유를 알 수 없었음

#### 18) `POST /api/auth/token/refresh/validate` — 로그인/로그아웃 - 리프레시 토큰 쿠키 존재 확인  (id 78)
**입력**
```
본문 없음 (refreshToken 쿠키)
```
**기대 출력**
```
200 (쿠키 있음) / 204 (쿠키 없음)
```
> 1기(2026-02~03) 개발. 자동 로그인 판정용

#### 19) `GET /api/auth/verification` — 비밀번호찾기 페이지 - 아이디+이메일 검증 후 인증번호 발송  (id 83)
**입력**
```
쿼리: ?loginId=hong&email=hong@pilsa.co.kr
```
**기대 출력**
```
{"message":"인증번호를 발송했습니다.","expireTime":300}
```
> 1기(2026-02~03) 개발. expireTime은 인증번호 만료까지 남은 초

### STEP 4. 회원 조회 — 계정: `t_stu` 토큰

#### 20) `GET /api/role` — 로그인 사용자 신분·관리권한 조회  (id 134)
**입력**
```
없음 (쿼리 없음)
```
**기대 출력**
```
{"memberType":"STUDENT","adminLevel":0}

※ memberType: STUDENT(재학생) / ALUMNI(졸업생)
※ adminLevel: 0=일반회원, 1~3=관리자
```
> 1기 응답은 {"role":"STUDENTS"} 하나였다. users.role 컬럼이 제거되고 member_type + admin_level 2축으로 갈리면서 두 값을 함께 내려준다. 경로는 1기와 동일하게 /api/role 유지

#### 21) `GET /api/user/boards` — 사이드바 게시판 목록 - 현재 로그인한 사람이 열람 가능한 게시판 목록  (id 1)
**입력**
```
없음 (쿼리 없음)
```
**기대 출력**
```
[
  {"boardId":1,"boardName":"공지사항","displayOrder":1}
]
```
> FE 메뉴는 이 API로 그린다 (하드코딩 금지)

#### 22) `GET /api/user/boards/{boardId}/categories` — 공통게시판 페이지 - 게시판 카테고리 목록  (id 3)
**입력**
```
없음
```
**기대 출력**
```
[{"categoryId":4,"name":"일상"},{"categoryId":5,"name":"질문"}]
```
> 구 /api/stu/{boardId}/categories

#### 23) `GET /api/user/boards/{boardId}/posts` — 공통게시판 페이지 - 게시글 목록 (페이징/검색/정렬/카테고리)  (id 4)
**입력**
```
쿼리: ?page=1&size=10&categoryId=4&keyword=검색어&sort=created|viewCount
```
**기대 출력**
```
{
  "totalPages":3, "totalCount":27,
  "posts":[{"postId":171,"title":"제목","authorName":"홍길동","likeCount":2,
    "viewCount":15,"commentCount":4,"categoryName":"일상","isPinned":false,
    "isAnonymous":false,"hasAttachment":true,"created":"2026-08-14T10:12:30"}]
}
※ isAnonymous=true면 authorName은 서버가 "익명"으로 마스킹
```
> 구 /api/stu/{boardId}/posts. 익명글 authorName 서버 마스킹

#### 24) `GET /api/user/boards/{boardId}/posts/top/{num}` — 공통게시판 페이지 - 상단 N개 (is_pinned 우선)  (id 14)
**입력**
```
경로변수: num = 가져올 글 개수 (1~50)
예) /posts/top/5 → 5건, /posts/top/3 → 3건
```
**기대 출력**
```
[{"postId":140,"title":"중요 공지","isPinned":true},
 {"postId":139,"title":"최근 글","isPinned":false}]

실패: 400 {"message":"조회 개수는 1 이상 50 이하여야 합니다."}
```
> 구 /api/stu/{boardId}/top5 (5건 고정) → 프론트가 요청한 num 만큼 반환. 중요(is_pinned) 글 우선, 그다음 최신순. state=normal 만

#### 25) `GET /api/user/boards/{boardId}/posts/{postId}` — 공통게시판 페이지 - 게시글 상세 (첨부·좋아요·이전다음). 댓글은 별도 API  (id 6)
**입력**
```
쿼리: ?sort=created|viewCount
```
**기대 출력**
```
{
  "postId":171,"boardId":2,"title":"제목","content":"본문",
  "userId":85,"authorName":"홍길동","categoryName":"일상",
  "isAnonymous":false,"isPinned":false,
  "viewCount":15,"likeCount":2,"isLiked":true,
  "commentCount":3,
  "created":"2026-08-14T10:12:30","updated":"2026-08-14T11:02:11",
  "prevPost":{"postId":159,"title":"이전 글 제목","categoryName":"질문","created":"2026-08-13T09:20:00"},
  "nextPost":{"postId":172,"title":"다음 글 제목","categoryName":"일상","created":"2026-08-14T15:40:00"},
  "attachments":[{"attachmentId":18,"originName":"파일.pdf","fileUrl":"uploads/board-2/uuid.pdf","fileSize":12345}],
  "attachmentCount":1
}

※ 댓글 본문은 내려가지 않는다 → GET .../posts/{postId}/comments 로 따로 조회
※ 익명글: authorName="익명", userId=null (관리자·작성자 본인 제외)
※ prevPost/nextPost: 첫 글·마지막 글이면 null
```
> 상세 응답은 created(생성일) + updated(수정일) 둘 다 내려간다(목록은 created만). 댓글 분리로 comments 배열은 제거되고 commentCount(노출 대상 댓글 수)만 남는다. 자기 글 조회 시에도 조회수는 증가한다

#### 26) `GET /api/user/boards/{boardId}/posts/{postId}/comments` — 공통게시판 페이지 - 댓글/대댓글 목록  (id 123)
**입력**
```
없음 (쿼리 없음)
```
**기대 출력**
```
[
  {"commentId":200,"parentCommentId":null,"content":"댓글","authorName":"관리자","userId":84,
   "isAnonymous":false,"isPrivate":false,
   "created":"2026-08-14T10:30:00","updated":null}
]

※ 익명댓글: authorName="익명", userId=null (관리자·댓글작성자 제외)
※ 비밀댓글: content="비밀댓글입니다." (관리자·댓글작성자·원글작성자 제외)
※ 대댓글은 parentCommentId로 표현 (무제한 깊이)
```
> state=normal 댓글만 내려간다 — 관리자가 블라인드(blind)했거나 삭제(deleted)한 댓글, 작성자가 지운 댓글은 목록에 포함되지 않는다. 마스킹은 전부 서버 책임

#### 29) `GET /api/user/mypage/toast/vapid-key` — 알림 발송 서버 공개키 (VAPID)  (id 138)
**입력**
```
없음
```
**기대 출력**
```
{"publicKey":"BKdQZg..."}
```
> 기기 등록 시 pushManager.subscribe 의 applicationServerKey 로 사용. 값 불변 — 프론트 상수 보관 가능

### STEP 5. 회원 쓰기 — 계정: `t_stu` 토큰 (알림 발생 확인은 `t_stu2` 가 t_stu 글에 댓글)

#### 30) `POST /api/user/boards/{boardId}/posts` — 게시글 등록 (multipart)  (id 5)
**입력**
```
요청 (multipart/form-data):
{
  "title": "제목",              ← 필수, 200자 이내
  "content": "본문(마크다운)",      ← 필수
  "categoryId": 12,
  "isAnonymous": false,
  "files": ["자료.pdf", "사진.png"],
  "draftId": 7                  ← 선택. 임시저장을 발행할 때만
}
```
**기대 출력**
```
{ "message": "게시글이 성공적으로 등록되었습니다.", "postId": 185 }

실패: 400 {"message":"제목은 필수입니다."}
     400 {"message":"제목은 200자를 넘을 수 없습니다."}
     400 {"message":"내용은 필수입니다."}
     403 {"message":"이 게시판에 글을 등록할 권한이 없습니다."}
```
> write_level 판정. 상단 고정은 isPinned 요청이 아니라 카테고리 '중요'(code=PINNED) 선택으로 서버가 결정(카테고리 목록에 관리자만 노출). draftId 가 오면 발행 성공과 같은 트랜잭션에서 해당 초안 삭제 — 없는/남의 draftId 는 무시하고 발행은 성공 (draftId 처리는 A-5 구현 시 추가)

#### 31) `PUT /api/user/boards/{boardId}/posts/{postId}` — 게시글 수정 (작성자/관리자)  (id 7)
**입력**
```
요청 (multipart/form-data):
{
  "title": "수정 제목",          ← 필수, 200자 이내
  "content": "수정 본문(마크다운)",  ← 필수
  "categoryId": 4,
  "isAnonymous": false,
  "deleteAttachmentIds": [18, 19],  ← 삭제할 기존 첨부만
  "files": ["새파일.pdf"]           ← 새로 추가할 첨부만
}
※ 유지할 기존 첨부는 아무것도 보내지 않는다 (증분 방식)
```
**기대 출력**
```
{"message":"게시글이 성공적으로 수정되었습니다."}

실패: 400 {"message":"제목은 필수입니다."} 등 검증 3종(등록과 동일)
     403 {"message":"수정 권한이 없습니다."}
     404 {"message":"수정할 수 없는 게시글입니다."} (블라인드/삭제 글)
```
> 응답은 message 만 — 수정 후 프론트가 상세로 이동하며 GET 을 다시 하므로 상세 객체 반환은 낭비(합의). 첨부 삭제는 소프트삭제(attachments.state=deleted). 중요 → 일반 카테고리로 바꾸면 상단 고정 자동 해제. 블라인드·삭제 글은 작성자도 수정 불가(증적 보호)

#### 32) `POST /api/user/boards/{boardId}/posts/{postId}/comments` — 공통게시판 페이지 - 댓글/대댓글 등록  (id 9)
**입력**
```
{"content":"댓글 내용","parentCommentId":null,
 "isAnonymous":false,"isPrivate":false}
※ parentCommentId 있으면 대댓글(무제한 깊이)
```
**기대 출력**
```
{"message":"댓글이 성공적으로 등록되었습니다."}

실패: 400 {"message":"답글을 달 부모 댓글이 존재하지 않습니다."}
     403 {"message":"이 게시판은 댓글을 사용하지 않습니다."}
```
> parentCommentId 무제한 깊이. 원글/부모 작성자에게 알림 발행

#### 33) `PUT /api/user/boards/{boardId}/posts/{postId}/comments/{commentId}` — 공통게시판 페이지 - 댓글/대댓글 수정  (id 10)
**입력**
```
{"content":"수정된 댓글","isAnonymous":false,"isPrivate":false}
```
**기대 출력**
```
{"message":"댓글이 성공적으로 수정되었습니다."}
```

#### 34) `PATCH /api/user/boards/{boardId}/posts/{postId}/comments/{commentId}/delete` — 공통게시판 페이지 - 댓글/대댓글 삭제 (소프트, 본인만)  (id 11)
**입력**
```
없음
```
**기대 출력**
```
{"message":"댓글이 성공적으로 삭제되었습니다."}

실패: 403 {"message":"본인 댓글만 삭제할 수 있습니다. (관리자 조치는 관리자 화면에서)"}
```

#### 35) `PATCH /api/user/boards/{boardId}/posts/{postId}/delete` — 공통게시판 페이지 - 게시글 삭제 (소프트, 작성자 본인만)  (id 8)
**입력**
```
없음
```
**기대 출력**
```
{"message":"게시글이 성공적으로 삭제되었습니다."}

실패: 403 {"message":"본인 글만 삭제할 수 있습니다. (관리자 조치는 관리자 게시글 관리에서)"}
     404 {"message":"존재하지 않는 게시글입니다."} (타 게시판 글·블라인드 글)
```
> 관리자 조치는 /api/admin/posts/{id} 사용(로그·벌점 연동)

#### 36) `PATCH /api/user/boards/{boardId}/posts/{postId}/like` — 공통게시판 페이지 - 좋아요 토글  (id 12)
**입력**
```
없음
```
**기대 출력**
```
{"message":"좋아요 +1"}  또는  {"message":"좋아요 취소"}
```

#### 37) `POST /api/user/mypage/toast/devices` — 알림 수신 기기 등록 (웹 푸시)  (id 136)
**입력**
```
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/abc...",
  "keys": { "p256dh": "BNc...", "auth": "k8J..." }
}
※ 원래는 브라우저 pushManager.subscribe() 결과를 프론트가 보내주지만,
   프론트가 없으므로 **위 더미 문자열을 그대로 넣어** 저장까지 확인하면 된다.
   (실제 발송 검증은 알림 담당자 과제 — HANDOFF-notification-tasks.md 에 방법을 넣어뒀다)
```
**기대 출력**
```
{"message":"알림 기기가 등록되었습니다."}

실패: 400 {"message":"기기 등록 정보가 올바르지 않습니다."}
```
> 확인: 같은 endpoint 로 **다시 등록해도 행이 늘지 않아야**(UPSERT) 하고,
> `endpoint` 를 비우거나 `keys` 를 빼면 **400**. `SELECT * FROM notification_devices;` 로 대조.
> 알림 켜기(권한 허용) 시 호출. 한 회원이 여러 기기 가능. 캘린더 구독과 무관한 웹 푸시 전달 채널. 테이블 notification_devices(세션성 — 물리삭제 예외)

#### 38) `DELETE /api/user/mypage/toast/devices` — 알림 수신 기기 해제  (id 137)
**입력**
```
{"endpoint": "https://fcm.googleapis.com/fcm/send/abc..."}
```
**기대 출력**
```
{"message":"알림 기기가 해제되었습니다."}
(이미 없는 기기여도 200)
```
> 알림 끄기·로그아웃 시 프론트가 pushManager 해제와 함께 호출. 본인 기기만 해제. 발송 응답 404/410 인 기기는 서버가 자동 정리

#### 42) `POST /api/user/reports` — 공통게시판/댓글 신고 페이지 - 게시글/댓글 신고 접수  (id 20)
**입력**
```
{"targetType":"post","targetId":171,"reasonId":1,
 "detail":"기타 사유일 때만 작성"}
※ targetType: post | comment
```
**기대 출력**
```
{"message":"신고가 접수되었습니다."}

실패: 400 {"message":"본인이 작성한 게시글/댓글은 신고할 수 없습니다."}
     409 {"message":"이미 신고한 게시글/댓글입니다."}
     409 {"message":"이미 삭제된 게시글/댓글입니다."}
```
> 구 /api/stu/reports. 중복 409, 본인 글 400, 삭제 대상 409

#### 42-1) `PATCH /api/user/mypage/withdraw` — 회원 탈퇴 (개인정보 파기)  (id 139)
**입력**
```
{"password":"본인 비밀번호"}

※ 본인 확인용 재입력 — 토큰 탈취만으로 탈퇴 불가.
※ 반드시 맨 마지막에, 버릴 계정으로! (가입→글 1개 작성→탈퇴 순서 추천)
```
**기대 출력**
```
{"message":"탈퇴 처리되었습니다."}

실패: 400 {"message":"비밀번호가 일치하지 않습니다."}
     404 {"message":"탈퇴 처리할 수 없는 계정입니다."} (이미 탈퇴한 계정 등)
```
> 성공 후 확인 6종: ①users 행 익명화(이름 '탈퇴한 회원', login_id deleted_{id}, 학번 del: 해시)
> ②그 계정이 쓴 글/댓글 작성자가 "탈퇴한 회원" 표시 ③알림 수신 기기 삭제(웹 푸시 중단)
> ④기존 액세스 토큰으로 아무 API 호출 → 401 (JWT 필터가 매 요청 DB 확인) ⑤관리자 제재 목록 미노출
> ⑥같은 학번 재가입 → 403 쿨다운 30일

#### 42-2) `PATCH /api/admin/users/{userId}/withdraw` — 회원 강제 탈퇴 (관리자)  (id 140)
**입력**
```
경로변수: userId (본문 없음) — **t_adm3(Lv3) 토큰 필수**
```
**기대 출력**
```
{"message":"강제 탈퇴 처리되었습니다.","userId":105}

실패: 404 {"message":"존재하지 않거나 이미 탈퇴한 회원입니다."}
     400 {"message":"관리자 계정은 강제 탈퇴할 수 없습니다. 관리 권한을 해제한 뒤 진행해주세요."}
```
> 처리 내용은 본인 탈퇴(42-1)와 동일 — 개인정보 파기 + 학번 해시 + 재가입 쿨다운. 관리자(admin_level≥1) 대상 시 400
> **Lv1·Lv2 토큰(t_adm1/t_adm2)으로 호출 → 403 "관리 레벨 3 이상만 사용할 수 있습니다." 도 함께 확인**

### STEP 6. 관리자 조회 — 계정: `t_adm3` 토큰

#### 43) `GET /api/admin/boards` — 게시판관리 페이지 - 게시판 목록 (게시글 수·권한 설정 포함)  (id 34)
**입력**
```
없음
```
**기대 출력**
```
[{"boardId":2,"boardName":"자유게시판","postCount":27,
  "readScope":"MEMBER","writeLevel":0,"displayOrder":2}]
```

#### 44) `GET /api/admin/posts` — 게시글관리 페이지 - 게시글 목록 (전 게시판, 검색)  (id 38)
**입력**
```
쿼리: ?page=1&size=10&boardId=2&keyword=제목또는글쓴이
```
**기대 출력**
```
{
  "totalPages":5,"totalCount":48,
  "posts":[{"postId":171,"boardId":2,"boardName":"자유게시판","title":"제목",
    "authorName":"홍길동","commentCount":4,"likeCount":2,"viewCount":15,
    "created":"2026-08-14T10:12:30","state":"normal"}]
}
※ state: normal|blind (deleted 제외)
```

#### 45) `GET /api/admin/posts/{postId}` — 게시글관리 페이지 - 게시글 상세 (blind/deleted 열람, 실작성자)  (id 39)
**입력**
```
없음
```
**기대 출력**
```
{
  "postId":171,"boardId":2,"boardName":"자유게시판","categoryName":"일상",
  "title":"제목","content":"본문","authorId":85,"authorName":"홍길동",
  "isAnonymous":false,"isPinned":false,"viewCount":15,"likeCount":2,
  "commentCount":4,"state":"blind",
  "created":"2026-08-14T10:12:30","updated":"2026-08-14T11:00:00",
  "attachments":[{"attachmentId":18,"originName":"파일1.png","fileSize":12345,"fileUrl":"/uploads/board-2/uuid.png"}],
  "comments":[{"commentId":200,"content":"댓글","userId":84,"authorName":"관리자",
    "isAnonymous":false,"isPrivate":false,"state":"normal",
    "created":"...","updated":null}]
}
※ 익명글도 실작성자 노출, 모든 state 댓글 포함, 조회수 미증가
```

#### 46) `GET /api/admin/quotes` — 문장 목록  (id 63)
**입력**
```
없음
```
**기대 출력**
```
{"quotes":[{"quoteId":6,"content":"문장","startDate":"2026-08-10",
  "endDate":"2026-08-16","writerId":1,"createdAt":"...","updatedAt":"..."}]}
```

#### 47) `GET /api/admin/reports/comments` — 신고관리페이지 - 신고된 댓글 목록 (원글 postId 포함)  (id 48)
**입력**
```
쿼리: ?page=1&size=10&state=blind&boardId=2&sort=&keyword=
```
**기대 출력**
```
{
  "totalPages":1,"totalCount":2,
  "items":[{"targetType":"comment","targetId":200,"postId":171,
    "preview":"댓글 앞부분","boardId":2,"boardName":"자유게시판",
    "authorName":"홍길동","reasonLabel":"광고/홍보",
    "firstReportedAt":"2026-08-14T10:05:00","reportCount":1,
    "state":"normal"}]
}
※ postId = 원문 이동용 게시글 id
```

#### 48) `GET /api/admin/reports/posts` — 신고관리페이지 - 신고된 게시글 목록 (대상별 그룹핑)  (id 49)
**입력**
```
쿼리: ?page=1&size=10&state=blind&boardId=2&sort=&keyword=
```
**기대 출력**
```
{
  "totalPages":1,"totalCount":4,
  "items":[{"targetType":"post","targetId":171,"postId":171,
    "preview":"본문 앞부분 30자","boardId":2,"boardName":"자유게시판",
    "authorName":"홍길동","reasonLabel":"욕설/비방",
    "firstReportedAt":"2026-08-14T10:00:00","reportCount":3,
    "state":"normal"}]
}
※ 대상 단위 그룹핑(동일 대상 중복 신고는 reportCount로 합산)
```

#### 49) `GET /api/admin/sanctions/users` — 제재회원목록 페이지 - 제재 회원 목록 (permanent/temporary/caution 태그)  (id 55)
**입력**
```
없음
```
**기대 출력**
```
[{"userId":75,"loginId":"user75","name":"홍길동","email":"a@b.c",
  "banStatus":"temporary","bannedUntil":"2026-08-21T23:59:59",
  "banStartedAt":"2026-08-14T10:00:00","tag":"temporary"}]
※ tag: permanent|temporary|caution
```

#### 50) `GET /api/admin/sanctions/users/{userId}` — 제재회원목록 페이지 - 제재 회원 상세 (누적주의/경고/신고삭제수)  (id 56)
**입력**
```
없음
```
**기대 출력**
```
{"tag":"temporary","banStatus":"temporary",
 "bannedUntil":"2026-08-21T23:59:59","banStartedAt":"2026-08-14T10:00:00",
 "cautionRemainder":2,"warningCount":1,"reportDeletedCount":3}
※ cautionRemainder = 유효 주의 합계 %% 10 (경고까지 남은 진행도)
※ warningCount 분모는 3 (ban_policy 3단계: 1주/1달/영구)
```
> 경고 단계는 3단계 확정 (시안 N/5는 오표기 → FE /3)

#### 51) `GET /api/admin/sanctions/users/{userId}/reports/comments` — 제재회원목록 페이지 - 회원별 신고된 댓글 내역  (id 126)
**입력**
```
없음 (쿼리 없음)
```
**기대 출력**
```
[
  {"reportId":11,"commentId":200,"postId":171,"boardId":2,"boardName":"자유게시판",
   "postTitle":"원글 제목","preview":"댓글 내용 앞 30자","state":"blind",
   "reasonId":1,"reasonLabel":"욕설/비방","detail":null,
   "status":"resolved","activeFlag":null,
   "createdAt":"2026-08-14T10:00:00","resolvedAt":"2026-08-14T11:00:00"}
]
```
> 게시글 신고와 분리(구 .../reports 통합 응답). 댓글은 제목이 없고 이동 경로가 소속 게시글이라 응답 형태가 다르다. state는 대상 댓글의 현재 표시 상태(normal/blind/deleted)

#### 52) `GET /api/admin/sanctions/users/{userId}/reports/posts` — 제재회원목록 페이지 - 회원별 신고된 게시글 내역  (id 58)
**입력**
```
없음
```
**기대 출력**
```
[
  {"reportId":9,"postId":171,"boardId":2,"boardName":"자유게시판",
   "title":"신고된 게시글 제목","preview":"본문 앞 30자","state":"normal",
   "reasonId":1,"reasonLabel":"욕설/비방","detail":null,
   "status":"resolved","activeFlag":null,
   "createdAt":"2026-08-14T10:00:00","resolvedAt":"2026-08-14T11:00:00"}
]
```
> 댓글 신고와 분리(구 .../reports 통합 응답에서 targetType 분기 제거). state는 대상 게시글의 현재 표시 상태(normal/blind/deleted)

#### 53) `GET /api/admin/users` — 회원목록 페이지 - 회원 목록 (검색/정렬/페이징/활동수/정지기간)  (id 59)
**입력**
```
쿼리: ?page=1&size=10&keyword=검색어&sort=
```
**기대 출력**
```
{
  "totalPages":3,"totalCount":25,
  "members":[{"userId":80,"loginId":"hong","name":"홍길동","phone":"010-1234-5678",
    "studentNo":"2026010101","email":"hong@pilsa.co.kr","memberType":"STUDENT",
    "adminLevel":0,"postCount":5,"commentCount":12,
    "banStartAt":null,"banEndAt":null,"banStatus":"none"}]
}
```
> 구 /api/admin/members

### STEP 7. 관리자 쓰기 — 계정: `t_adm3` 토큰

#### 54) `POST /api/admin/boards` — 게시판관리 페이지 - 새 게시판 생성 (read_scope/write_level 등)  (id 35)
**입력**
```
{"name":"동문 게시판","readScope":"ALUMNI","writeLevel":0}
※ readScope: MEMBER(재학생+졸업생)|STUDENT(재학생)|ALUMNI(졸업생), writeLevel: 0~3 (0은 일반회원을 의미)
```
**기대 출력**
```
201 Created
{"boardId":4,"boardName":"동문 게시판","postCount":0,
 "readScope":"ALUMNI","writeLevel":0,"displayOrder":4}

실패: 409 {"message":"이미 존재하는 게시판 이름입니다."}
```
> 생성 즉시 /api/boards/{id}/** 동작 (코드 수정 불필요)

#### 55) `PATCH /api/admin/boards/{boardId}` — 게시판관리 페이지 - 게시판 수정 (전달 필드만)  (id 36)
**입력**
```
{"name":"이름 변경","readScope":"MEMBER","writeLevel":1,"displayOrder":3}
※ 전달한 필드만 수정
```
**기대 출력**
```
{
  "boardId":4,"boardName":"동문 게시판","postCount":12,
  "readScope":"ALUMNI","writeLevel":0,"displayOrder":4,
  "allowComment":true,"allowAttachment":true,"categoryMode":false,
  "defaultCategoryId":null,"allowAnonymous":false,"allowPrivateComment":false
}

실패: 404 {"message":"존재하지 않는 게시판입니다."}
     409 {"message":"이미 존재하는 게시판 이름입니다."}
     400 {"message":"열람 권한 값이 올바르지 않습니다. (MEMBER=재학+졸업 / STUDENT=재학 / ALUMNI=졸업)"}
```
> 전달한 필드만 수정된다. 수정 후 게시판 정보 전체를 반환하므로 프론트가 재조회할 필요 없다

#### 56) `PATCH /api/admin/boards/{boardId}/delete` — 게시판관리 페이지 - 게시판 삭제 (소프트, 글 있으면 409)  (id 37)
**입력**
```
없음
```
**기대 출력**
```
{"message":"게시판이 삭제되었습니다."}

실패: 409 {"message":"게시글이 3건 남아 있어 삭제할 수 없습니다."}
```

#### 57) `POST /api/admin/event` — (관리자) 일정(캘린더) 페이지 - 일정 등록  (id 68)
**입력**
```
{
  "title": "3월 정기모임",
  "category": "정기모임",
  "description": "3월 정기모임 안내",
  "startDate": "2026-03-01",
  "endDate": "2026-03-01"
}

※ description은 DB NOT NULL, category는 관리자 자유 입력(varchar 50, NULL 허용)
```
**기대 출력**
```
201 Created
{
  "message": "새로운 일정이 등록되었습니다.",
  "data": {"eventId": 12, "title": "3월 정기모임"}
}

실패: 400 {"message":"시작일과 종료일은 필수 입력 항목입니다."}
     400 {"message":"시작일이 종료일보다 늦을 수 없습니다."}
     403 {"message":"관리자 권한이 필요합니다."}
```
> 성공 코드가 200이 아니라 201. startDate/endDate는 YYYY-MM-DD 문자열을 그대로 datetime 컬럼에 저장하므로 시각은 00:00:00으로 들어감. 등록자 user_id는 AuthUtils.currentUserId()로 자동 기록

#### 58) `PUT /api/admin/event/{eventId}` — (관리자) 일정(캘린더) 페이지 - 일정 수정 (부분 수정)  (id 71)
**입력**
```
{
  "title": "3월 정기모임(장소 변경)",
  "category": "정기모임",
  "description": "장소가 변경되었습니다.",
  "startDate": "2026-03-02",
  "endDate": "2026-03-02"
}

※ 전달한 필드만 반영(전부 선택)
```
**기대 출력**
```
{
  "message": "일정 정보가 성공적으로 수정되었습니다.",
  "data": {"eventId": 12, "updatedAt": "2026-08-15T15:20:00"}
}

실패: 404 {"message":"해당 일정을 찾을 수 없습니다."} (없거나 이미 삭제된 일정)
     403 {"message":"관리자 권한이 필요합니다."}
```
> MyBatis <set><if>로 전달 필드만 UPDATE. WHERE에 state=normal이 있어 삭제된 일정은 404. 등록과 달리 수정에는 시작일<=종료일 검증이 없음(유의)

#### 59) `DELETE /api/admin/event/{eventId}` — (관리자) 일정(캘린더) 페이지 - 일정 삭제 (소프트)  (id 122)
**입력**
```
경로변수: eventId (본문 없음)
```
**기대 출력**
```
{
  "message": "일정이 정상적으로 삭제되었습니다."
}
※ data는 null이라 응답 JSON에서 생략됨(@JsonInclude NON_NULL)

실패: 404 {"message":"삭제할 일정을 찾을 수 없습니다."}
     403 {"message":"관리자 권한이 필요합니다."}
```
> HTTP 메서드는 DELETE지만 실제 동작은 소프트삭제(events.state = deleted). 이미 삭제된 일정을 다시 지우면 404

#### 60) `POST /api/admin/quotes` — 문장 등록 (노출기간)  (id 64)
**입력**
```
{"content":"오늘 쓴 한 문장이 내일의 나를 만든다.",
 "startDate":"2026-08-17","endDate":"2026-08-23"}
```
**기대 출력**
```
201 Created
{"message":"문장이 등록되었습니다.","data":{"quoteId":10}}
```

#### 61) `PUT /api/admin/quotes/{quoteId}` — 문장 수정  (id 65)
**입력**
```
{"content":"수정된 문장","startDate":"2026-08-17","endDate":"2026-08-23"}
```
**기대 출력**
```
{"message":"문장이 수정되었습니다.","data":null}
```

#### 62) `PATCH /api/admin/quotes/{quoteId}/delete` — 문장 삭제 (소프트)  (id 66)
**입력**
```
없음
```
**기대 출력**
```
{"message":"문장이 삭제되었습니다.","data":null}
※ 소프트삭제(state=deleted)
```
> 2기: 물리삭제 → 소프트삭제 전환

#### 63) `PATCH /api/admin/reports/select-blind` — 신고/게시글/댓글관리페이지 - 신고 선택 블라인드 (일괄)  (id 52)
**입력**
```
{
  "targetType": "post",
  "targetIds": [171, 172],
  "reasonId": 5,
  "detail": "기타 사유일 때만"
}

or

{
  "targetType": "comment",
  "targetIds": [200, 201],
  "reasonId": 5,
  "detail": "기타 사유일 때만"
}
```
**기대 출력**
```
{"successCount":2,"failCount":0,"failures":[]}

※ 항목마다 독립 트랜잭션 — 일부가 실패해도 나머지는 그대로 처리된다(부분 성공)
※ 요청에 중복 id가 있으면 한 번만 처리된다
```
> 단건 조치용 API는 없다 — targetIds 에 1건만 담아 호출한다. state=blind 로 가리기만 하며 벌점은 부과하지 않는다(삭제와의 차이). 최종 판단 전 임시 조치이므로 신고는 pending 으로 남는다

#### 64) `PATCH /api/admin/reports/select-delete` — 신고/게시글/댓글관리페이지 - 신고 선택 삭제 (일괄)  (id 51)
**입력**
```
{
  "targetType": "post",
  "targetIds": [171, 172],
  "reasonId": 1,
  "detail": "기타 사유일 때만"
}

or 

{
  "targetType": "comment",
  "targetIds": [200, 201],
  "reasonId": 1,
  "detail": "기타 사유일 때만"
}
```
**기대 출력**
```
{"successCount":1,"failCount":1,
 "failures":[{"id":172,"message":"이미 삭제된 게시글입니다."}]}

※ 항목마다 독립 트랜잭션 — 일부가 실패해도 나머지는 그대로 처리된다(부분 성공)
※ 요청에 중복 id가 있으면 한 번만 처리된다
```
> 단건 조치용 API는 없다 — targetIds 에 1건만 담아 호출한다. 소프트 삭제(state=deleted) + 작성자 주의 +2 + 경고/정지 에스컬레이션. 대상별 pending 신고를 resolved로 일괄 종료(중복 신고 이중 벌점 차단). reasonId 미전달 시 대표(최신) 신고 사유를 사용하므로 신고 없는 게시글도 이 API로 삭제 가능

#### 65) `PATCH /api/admin/reports/select-restore` — 신고관리페이지 - 신고 선택 복원 (일괄)  (id 50)
**입력**
```
{
  "targetType": "post",
  "targetIds": [171, 172]
}

or

{
  "targetType": "comment",
  "targetIds": [200, 201]
}

※ 복원은 사유를 받지 않는다
```
**기대 출력**
```
{"successCount":1,"failCount":1,
 "failures":[{"id":172,"message":"존재하지 않는 게시글입니다."}]}

※ 항목마다 독립 트랜잭션 — 일부가 실패해도 나머지는 그대로 처리된다(부분 성공)
※ 요청에 중복 id가 있으면 한 번만 처리된다
```
> 단건 조치용 API는 없다 — targetIds 에 1건만 담아 호출한다. 신고관리/게시글관리/댓글관리 화면이 공유. 이미 삭제(deleted)된 대상은 되살리지 않는다(의도적 삭제·벌점 보호). 처리 결과는 대상별 pending 신고를 rejected로 종료

#### 66) `POST /api/admin/sanctions/users/{userId}/lift` — (3기 진행 예정) 제재 수동 해제  (id 57)
**입력**
```
없음
```
**기대 출력**
```
{"message":"제재가 해제되었습니다."}
※ ban_status=none + 열린 ban_log 전부 해제(lifted_by 기록)
```

#### 67) `PATCH /api/admin/users/ban` — 회원목록 페이지 - 회원 영구차단 (단일/다중)  (id 62)
**입력**
```
{"userIds":[80,81,82]}
```
**기대 출력**
```
{"message":"영구차단 처리되었습니다.","userId":null}

실패: 404 (없는 id가 하나라도 있으면 전체 실패)
```
> all-or-nothing

#### 68) `PATCH /api/admin/users/{userId}` — 회원목록 페이지 - 회원 정보 수정 (부분)  (id 60)
**입력**
```
{"name":"홍길동","phone":"010-1234-5678","studentNo":"2026010101","memberType":"ALUMNI","adminLevel":1}
※ 전달한 필드만 수정됨
※ email 은 수정 불가
```
**기대 출력**
```
{"message":"회원 정보가 수정되었습니다.","userId":80}

실패: 400 {"message":"유효하지 않은 회원 구분 값입니다. (STUDENT/ALUMNI)"}
     409 {"message":"이미 사용 중인 이메일입니다."}
```
> memberType/adminLevel 검증, 중복 검사

#### 69) `PATCH /api/admin/users/{userId}/suspend` — 회원목록 페이지 - 회원 정지 (temporary)  (id 61)
**입력**
```
{"endDate":"2026-09-30"}
※ 종료일 23:59:59까지 정지
```
**기대 출력**
```
{"message":"회원이 정지되었습니다.","userId":80}

실패: 400 {"message":"정지 종료일은 현재보다 미래여야 합니다."}
     409 {"message":"이미 영구차단된 회원입니다. 정지로 변경할 수 없습니다."}
```
> ban_log source=manual, warning_no=NULL

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
- [ ] 첨부 삭제 후 `uploads/board-2/{postId}/` 폴더에서 **실제 파일도 사라지고, 폴더가 비면 폴더 자체도 삭제됨** (uploads 는 유지)
- [ ] 상세에 comments 없음 + **댓글 별도 API** 동작
- [ ] `posts/top/{num}` 이 요청 개수만큼
- [ ] 관리자 조치가 **select-* 3종**으로만 되고 부분 성공 응답
- [ ] 관리자 수정/정지/차단/삭제가 **PATCH**
- [ ] 게시판 생성 시 `readScope:"ALL"` → 400
- [ ] ~~댓글/대댓글 알림~~ → **발행 기능이 담당자 과제로 환원되어 현재 알림이 생기지 않는다**(정상). 구현 후 검증
- [ ] 회원가입: 인증번호 검증 없이 바로 register 호출 → **403 "이메일 인증이 완료되지 않았거나 만료되었습니다. 이메일 인증을 다시 진행해주세요."**
- [ ] 비밀번호 초기화(84): 인증 검증 없이 바로 호출 → **401** (기존엔 아이디만 알면 변경 가능했던 구멍)
- [ ] 회원가입 형식 위반 → 각각 **400 필드별 message** (학번 8자리 / 아이디 4자 / 비밀번호 "weak" / 전화 01012345678 / 이름 1자)
- [ ] 인증 플래그 1회용: 가입 성공 직후 같은 인증으로 다시 register → **403** (재인증 필요)
- [ ] 인증 플래그 만료: policy_settings.mail_verified_ttl_minutes 를 1로 낮추고 1분 뒤 register → **403**, 테스트 후 30 원복
- [ ] 비밀번호 초기화(84): 새 비밀번호 "weak" → **400 "비밀번호는 문자, 숫자, 특수문자를 포함한 8~20자여야 합니다."**
- [ ] 탈퇴 직후 같은 학번 재가입 → **403 "탈퇴 후 30일 동안 재가입할 수 없습니다."** (쿨다운)
- [ ] 탈퇴(139): 새 계정 하나 가입→탈퇴 후 ①그 계정 글 작성자가 "탈퇴한 회원"으로 표시 ②같은 학번으로 재가입 성공 ③관리자 제재 목록에 미노출. 영구차단 후 탈퇴한 학번으로 재가입 → 403 "가입이 제한된 학번입니다"
- [ ] 강제 탈퇴(140): **Lv3(t_adm3)만 가능** — Lv1·Lv2 호출 시 403 / 일반 회원 대상 성공 / 관리자(t_adm1~3) 대상 → **400** / 이미 탈퇴한 userId → **404**
- [ ] 정지 중 탈퇴한 학번으로 재가입 → **403 "가입이 제한된 학번입니다. (YYYY-MM-DD 이후 가입 가능)"**
- [ ] 탈퇴 직후 기존 액세스 토큰으로 API 호출 → **401** (JWT 필터가 매 요청 DB 재조회)

## 5. 스웨거로 테스트하는 법

주소: `http://localhost:8080/swagger-ui/index.html` (상단 filter 로 검색, 도메인별 태그)

**① 로그인 → 토큰 물리기 (이걸 안 하면 전부 401)**
1. `POST /api/auth/login` 실행 → 응답의 `accessToken` 복사
2. 우측 상단 **Authorize** → `Bearer eyJ...` 가 아니라 **토큰 값만** 붙여넣기 → Authorize
3. 계정을 바꿀 때마다 Authorize 를 다시 해야 한다 (t_stu → t_adm3 등)

**② 파일 업로드가 있는 API** (게시글 등록·수정)
`multipart/form-data` 라 스웨거에서 파일 선택 UI 가 뜬다. 파일 없이 보내려면 그 칸을 비우고 실행.
한글 파일명도 테스트할 것 (원본 파일명 저장 확인).

**③ 날짜 파라미터**
`YYYY-MM-DD` 문자열이다. 예: `2026-03-01`. 시간까지 넣으면 400.

---

## 6. 실패 시 기록
`confirmed_at` 을 채우지 말고, 실패 내용을 이 문서 아래에 추가하거나 바로 알려줄 것.
(요청/응답 전문 + 서버 로그가 있으면 원인 파악이 빠름)
