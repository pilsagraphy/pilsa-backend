# API 엔드포인트 재설계 — 마이그레이션 표 (프론트 전달용)

작성: 2026-08-14 / 대상 브랜치: `20260814`

## 설계 규칙

1. **신분(stu/alu) 접두사 폐지.** 권한은 URL이 아니라 데이터(`boards.read_scope`/`write_level`)와
   각 서비스의 `AuthUtils` 판정으로 결정한다. 관리자가 런타임에 만든 게시판은 정적 URL로 표현할 수 없다.
2. **리소스 기준 경로.** `/api/{리소스}/{id}/{하위리소스}` 형태의 복수형 명사.
3. `/api/admin/**` 은 **관리자 화면 전용** 묶음으로 유지 (URL 레벨 1차 방어선 + 화면 단위 구분).
4. `/api/public/**` **접두사 폐지** → 리소스 경로로 쓰고, 공개 여부는 SecurityConfig에 개별 명시.
5. 내 정보 관련은 `/api/mypage/**`.

## 변경 표

### 게시판 (가장 큰 변경)

| 구 경로 | 신 경로 |
|---------|---------|
| `GET /api/stu/{boardId}/posts` | `GET /api/boards/{boardId}/posts` |
| `POST /api/stu/{boardId}/posts` | `POST /api/boards/{boardId}/posts` |
| `GET /api/stu/{boardId}/posts/{postId}` | `GET /api/boards/{boardId}/posts/{postId}` |
| `PUT /api/stu/{boardId}/posts/{postId}` | `PUT /api/boards/{boardId}/posts/{postId}` |
| `DELETE /api/stu/{boardId}/posts/{postId}` | `DELETE /api/boards/{boardId}/posts/{postId}` |
| `PATCH /api/stu/{boardId}/posts/{postId}/like` | `PATCH /api/boards/{boardId}/posts/{postId}/like` |
| `POST /api/stu/{boardId}/posts/{postId}/comments` | `POST /api/boards/{boardId}/posts/{postId}/comments` |
| `PUT /api/stu/{boardId}/posts/{postId}/comments/{commentId}` | `PUT /api/boards/{boardId}/posts/{postId}/comments/{commentId}` |
| `DELETE .../comments/{commentId}` | `DELETE /api/boards/{boardId}/posts/{postId}/comments/{commentId}` |
| `GET /api/stu/{boardId}/categories` | `GET /api/boards/{boardId}/categories` |
| `GET /api/stu/{boardId}/top5` | `GET /api/boards/{boardId}/posts/top5` |
| — | **`GET /api/boards` (신규)** |

> **`GET /api/boards` 는 새로 추가한 필수 API다.** 게시판이 데이터가 되어(관리자가 런타임 생성)
> 프론트가 게시판 목록을 하드코딩할 수 없다. 응답은 **현재 사용자가 열람 가능한 게시판만** 노출 순서대로 준다.
> ```json
> [{ "boardId":2, "boardName":"자유게시판", "displayOrder":2, "canWrite":true,
>    "allowComment":true, "allowAttachment":true, "categoryMode":true,
>    "allowAnonymous":true, "allowPrivateComment":false }]
> ```
> `canWrite` 로 글쓰기 버튼 노출을, `categoryMode`/`allowAnonymous`/`allowPrivateComment` 로
> 입력 UI 노출을 판단하면 게시판이 늘어나도 프론트 수정이 불필요하다.

### 마이페이지

| 구 경로 | 신 경로 |
|---------|---------|
| `GET /api/role` | `GET /api/mypage/profile` |
| `GET /api/notifications` | `GET /api/mypage/notifications` |
| `GET /api/notifications/unread-count` | `GET /api/mypage/notifications/unread-count` |
| `PATCH /api/notifications/{id}/read` | `PATCH /api/mypage/notifications/{id}/read` |
| `PATCH /api/notifications/read-all` | `PATCH /api/mypage/notifications/read-all` |
| `DELETE /api/notifications/{id}` | `DELETE /api/mypage/notifications/{id}` |

### 공개 콘텐츠 (`/api/public/**` 폐지)

| 구 경로 | 신 경로 |
|---------|---------|
| `GET /api/public/honor/` | `GET /api/donations` |
| `GET /api/public/quotes/random` | `GET /api/quotes/current` |
| `GET /api/public/schedules` | `GET /api/events` |

### 관리자

| 구 경로 | 신 경로 |
|---------|---------|
| `GET /api/admin/members` | `GET /api/admin/users` |
| `PUT /api/admin/members/{userId}` | `PUT /api/admin/users/{userId}` |
| `POST /api/admin/members/{userId}/suspend` | `POST /api/admin/users/{userId}/suspend` |
| `POST /api/admin/members/ban` | `POST /api/admin/users/ban` |
| `POST /api/admin/schedules` | `POST /api/admin/events` |
| `PUT /api/admin/schedules/{id}` | `PUT /api/admin/events/{id}` |
| `DELETE /api/admin/schedules/{id}` | `DELETE /api/admin/events/{id}` |

관리자 게시판 관리(`/api/admin/boards`)는 이번에 신설된 API다 (목록/생성/수정/삭제).

### 신고

| 구 경로 | 신 경로 |
|---------|---------|
| `POST /api/stu/reports` | `POST /api/reports` |

신고는 재학생/졸업생/관리자 구분 없이 **로그인 회원 공통** 기능이므로 신분 접두사를 뺐다.

### 메일 (표기 불일치 정리)

| 구 경로 | 신 경로 |
|---------|---------|
| `POST /api/mail/verifycode` | `POST /api/mail/verification-code` |
| `POST /api/mail/verifycode/verify` | `POST /api/mail/verification-code/verify` |

`GET /api/mail/verification-code/ttl` 은 변경 없음.

### 변경하지 않은 것

- **`/api/auth/**` 전체 유지.** 리프레시 토큰 쿠키의 `path`가 `/api/auth/token`으로 고정돼 있어
  경로를 바꾸면 쿠키 설정까지 함께 손봐야 하고, 이름 자체에 문제가 없어 리스크 대비 이득이 없다.

## 응답 형식 변경

에러 응답이 **평문 → JSON 객체**로 통일됐다.
```json
{ "message": "이 게시판을 열람할 권한이 없습니다." }
```
정지/차단 시에는 필드가 추가된다 (시안 p14 "N월 N일부터 다시 로그인 할 수 있습니다" 대응).
```json
{ "message": "정지된 계정입니다.", "banType": "temporary", "bannedUntil": "2026-03-30T23:59:59" }
```

> **프론트 영향 없음(확인 완료).** `apis/auth.js`의 `getErrorMessage()`가
> `typeof data === 'string'` 과 `data.message` 를 **둘 다** 처리하고 있고(52곳에서 사용),
> `LoginSection.jsx`는 오히려 `.message`를 먼저 본다. 즉 기존 코드 그대로 동작한다.

또한 매핑되지 않은 경로는 **404**를 반환한다(`{"message":"요청하신 경로를 찾을 수 없습니다."}`).

## 필드명 변경

| 구 필드 | 신 필드 | 비고 |
|---------|---------|------|
| `boardCode` (NOTICE/FREE/INFO) | `boardName` (공지사항/자유게시판/정보게시판) | 한글명이 DB에 저장되어 프론트 매핑 불필요 |
| `pinned` | `isPinned` | 기존에 `boolean isPinned` 필드가 자바 빈 규약상 `pinned`으로 나가던 버그 수정 |
| `anonymous` | `isAnonymous` | 동상 |
| `private` | `isPrivate` | 동상 |
| `liked` | `isLiked` | 동상 |

> 요청 쪽도 동일하다. 기존에는 폼 키 `isPinned`가 **바인딩되지 않아 관리자가 상단 고정을 해도 저장되지 않았다.**
> 지금은 `isPinned`/`isAnonymous`/`isPrivate` 그대로 보내면 된다.

## 동작 변경 (2026-08-14 최종 검토 반영)

| 변경 | 내용 |
|------|------|
| 익명글 마스킹 | 익명글의 `authorName`은 서버에서 `"익명"`으로, `userId`는 `null`로 내려간다 (목록·상세 공통). **관리자와 작성자 본인에게만** 실명·id가 보인다 — FE 마스킹 코드는 제거해도 된다 |
| 비밀댓글 마스킹 | 비밀댓글 `content`는 관리자/댓글 작성자/원글 작성자 외에는 `"비밀댓글입니다."`로 내려간다 |
| 목록 응답 | `GET /api/boards/{boardId}/posts` 목록 행에 `isAnonymous` 필드 추가 |
| 게시글/댓글 삭제 | **작성자 본인만** 가능(관리자 포함 타인 글은 403) — 관리자 조치는 `/api/admin/posts/**` 사용(조치 로그·벌점 연동) |
| 블라인드 글 자삭 차단 | 블라인드된 본인 글/댓글은 삭제 불가(404) — 제재 회피 방지 |
| 만료 토큰 + 공개 API | 만료/무효 토큰을 달고 공개 리소스(`/api/donations` 등)를 호출해도 401이 아니라 정상 응답 |
| 401/403/410 본문 | 시큐리티 레벨 거부도 이제 전부 `{"message": ...}` JSON 본문을 갖는다. 세션 중 차단 403은 `banType`/`bannedUntil` 필드 포함 (로그인 403과 동일 형태) |
