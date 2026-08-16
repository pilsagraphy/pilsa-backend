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

## 1. 준비 — 계정과 기본 데이터

DB에 이미 있는 계정 (비밀번호는 각자 설정값 사용):

| 용도 | loginId | 신분 | 관리레벨 | 비고 |
|---|---|---|---|---|
| 관리자 | `admin_pilsa` | STUDENT | 3 | 관리자 API 전용 |
| 관리자(본인) | `wm5256` | ALUMNI | 3 | |
| 일반 재학생 | `purpletree0210` | STUDENT | 0 | 회원 API 주 계정 |
| 일반 재학생2 | `ahnyejii` | STUDENT | 0 | 신고·댓글 상대역 |
| 정지 계정 | `test_susp1w` | STUDENT | 0 | 로그인 403 확인용 |
| 영구차단 계정 | `test_banned` | STUDENT | 0 | 로그인 403 확인용 |

기본 데이터: 게시판 3개(1=공지사항 write_level=1, 2=자유게시판, 3=정보게시판), 신고 사유 8종,
게시글 13건, 일정 1건, 문장 9건, 후원 3건.

**스웨거에서 토큰 넣는 법**: 로그인 응답의 `accessToken` 복사 → 우측 상단 **Authorize** → `Bearer {토큰}` 입력.
관리자 API 테스트 전에는 관리자 계정 토큰으로 다시 Authorize 할 것.

---

## 2. 테스트 순서 (요청하신 흐름 → 쉬운 것 → 복잡한 것)

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
