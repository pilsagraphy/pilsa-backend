# [전달] 게시글 리치 에디터 — 프론트 API 계약 (2026-08-16 확정 / 2026-08-23 선업로드 구현 반영)

> 대상: 프론트엔드. 글쓰기/수정/임시저장/이미지/첨부가 서버와 어떻게 주고받는지 전부 이 문서에 있습니다.
> 요청·응답의 전체 예시는 Swagger의 **"게시판(글·댓글)"** 태그 참고.

## 0. 확정된 원칙 3가지

1. **본문(content)은 마크다운 문자열** — HTML 아님. 서버는 받은 마크다운을 그대로 저장·반환하고,
   화면에 그릴 때(마크다운→HTML 변환 단계) **새니타이즈는 프론트 책임**이다.
2. **이미지는 "선업로드"** — 발행할 때 올리는 게 아니라, 에디터에 이미지를 넣는 그 순간 서버에 올린다. (§2)
3. **상단 고정은 요청 필드가 아니다** — isPinned 라는 필드는 어디에도 없다. 카테고리에서 '중요'를
   고르면(관리자에게만 목록에 보임) 서버가 알아서 고정한다.

## 1. 글쓰기 화면의 전체 흐름

```
1. 카테고리 목록     GET /api/user/boards/{boardId}/categories     ← 관리자에게만 '중요' 포함됨. 받은 대로 그리면 끝
2. 본문 작성         content = 에디터의 마크다운 문자열
3. 이미지 삽입       그 순간 선업로드(§2) → 응답의 markdown 을 본문에 그대로 삽입
4. 파일 첨부         같은 선업로드 API 에 usage=attachment (또는 발행 multipart 의 files[] — 둘 다 지원)
5. 글 저장하기(임시)  POST .../drafts → draftId 보관. 2번째부터는 PUT .../drafts/{draftId} 덮어쓰기
6. 발행              POST .../posts (multipart) + attachmentIds — draftId 를 넣으면 그 초안은 서버가 자동 삭제
                     → 응답의 postId 로 상세 페이지 이동
```

## 2. 선업로드 (2026-08-23 구현 완료 — 깃허브 에디터와 같은 방식)

이미지를 에디터에 넣는 순간 **화면에 보여줄 URL이 즉시 필요**하다. 발행 시점에 올리는 방식으로는
에디터가 이미지를 표시할 방법이 없으므로, 삽입 즉시 업로드하고 URL을 받아 본문 텍스트에 심는다.

```
POST /api/user/boards/{boardId}/files      (multipart/form-data)
  file  : 파일 1개 (필수)
  usage : inline | attachment (선택 — 생략 시 이미지는 inline, 그 외는 attachment)

→ 200 {"attachmentId":31, "url":"/api/user/files/31", "originName":"스크린샷.png",
        "fileSize":12345, "isImage":true, "usageType":"inline",
        "markdown":"![스크린샷.png](/api/user/files/31)"}
```

**깃허브와 동일한 UX 구현 순서** (스크린샷의 `![Uploading …]()` 자리표시자 방식):

```js
// 1) 붙여넣기·드래그·파일선택 즉시 자리표시자를 본문에 넣는다
const placeholder = `![Uploading ${file.name}…]()`;
insertAtCursor(placeholder);

// 2) 업로드
const form = new FormData(); form.append('file', file);
const { markdown } = await api.post(`/api/user/boards/${boardId}/files`, form);

// 3) 자리표시자를 응답의 markdown 으로 교체 (실패하면 자리표시자를 지운다)
replaceText(placeholder, markdown);
```

- `markdown` 은 서버가 완성해서 준다 — 이미지면 `![파일명](url)`, 그 외는 `[파일명](url)`
  (파일명 속 대괄호는 서버가 이스케이프). 프론트가 문법을 조립할 필요가 없다.
- **inline vs attachment**: `usage=inline` 인 이미지는 본문에만 보이고 **상세의 첨부 목록에는 나오지 않는다**
  (본문에 이미 보이는 이미지가 목록에 중복 노출되지 않게). `usage=attachment` 는 첨부 목록에 노출된다.
- **임시저장이어도 동일하다.** 초안에는 `![](url)` 텍스트만 저장되고 이미지 파일은 이미 서버에 있다.
- 첨부파일(pdf 등)도 이제 같은 API 로 미리 올릴 수 있다. 발행 multipart 의 `files[]` 방식도 그대로 지원한다.
- 허용 확장자는 서버 정책값이다 — `policy_settings.upload_image_extensions` / `upload_file_extensions`
  (허용 외 확장자는 400 `허용되지 않는 파일 형식입니다.`). 요청 상한은 30MB(초과 시 413).
  `usage` 에 inline/attachment 외의 값을 보내면 400 `usage 는 inline 또는 attachment 만...` (대소문자는 무시된다).

### 올린 파일은 언제 글의 것이 되는가

발행·수정 요청의 **`attachmentIds`** 에 넣으면 그 글에 연결된다.
본문에 `/api/user/files/{id}` 가 남아 있으면 attachmentIds 에 빠뜨려도 **서버가 본문을 훑어 함께 연결**하므로,
인라인 이미지는 프론트가 따로 id 목록을 관리하지 않아도 된다.

> ⚠️ **글에 연결되지 않은 파일은 24시간 뒤 새벽 배치가 삭제한다**
> (`policy_settings.pending_upload_purge_hours`). 작성 도중 이탈한 파일을 정리하는 장치다.
> 수정 저장 시 **본문에서 지운 인라인 이미지도 함께 삭제**된다 — 마크다운이 곧 기준이다.

## 2-1. 파일 조회는 인증형이다 — img 태그로 바로 못 붙인다

```
GET /api/user/files/{fileId}        ← 업로드 응답의 url. Authorization 헤더 필요
```
이 API 가 **첨부 파일 접근의 유일한 경로**다 — 첨부 정적 서빙(`/uploads/board-*`)은 폐지됐다(2026-08-23 PM 결정.
URL 만 알면 비로그인도 열리던 구멍. 명예의전당 사진 `/uploads/Honor/**` 만 공개 정적 서빙 유지).
파일 → 글 → 게시판을 타고 열람 권한(read_scope)을 검사하며, 권한이 없으면 404(존재 자체를 알려주지 않는다).
아직 글에 연결되지 않은 파일(초안 포함)은 올린 본인만 볼 수 있다.

`img` 태그는 Authorization 헤더를 붙일 수 없으므로, 마크다운을 그릴 때 **이미지 컴포넌트를 갈아끼워
한 번 fetch 한 뒤 blob URL 로 표시**한다(첨부 다운로드도 같은 방식).

```js
const res  = await fetch(src, { headers: { Authorization: `Bearer ${token}` } });
const blob = await res.blob();
setUrl(URL.createObjectURL(blob));      // 언마운트 시 URL.revokeObjectURL
```
이미지는 `Content-Disposition: inline`, 그 외 파일은 `attachment; filename="원본명"` 으로 내려온다.

## 3. API 계약 요약

### 파일 업로드 — `POST /api/user/boards/{boardId}/files` (multipart/form-data) ✅구현됨(2026-08-23)
```
file(필수, 1개) / usage(선택: inline|attachment)
→ 200 { attachmentId, url, originName, fileSize, isImage, usageType, markdown }   ← §2
→ 400 허용되지 않는 파일 형식 · 업로드할 파일 없음 · usage=inline 인데 비이미지
→ 403 작성 권한 없음 / 파일 업로드를 쓰지 않는 게시판 / 413 30MB 초과
```

### 파일 조회 — `GET /api/user/files/{fileId}` ✅구현됨(2026-08-23)
```
→ 200 파일 스트림 (이미지=inline, 그 외=attachment; filename="원본명")
→ 404 없는 파일 · 삭제된 첨부 · 열람 권한 없는 게시판 · 블라인드/삭제된 글의 첨부 · 남의 미연결 파일
※ img 태그로 직접 못 붙인다 — blob 방식 (§2-1)
```

### 게시글 등록 — `POST /api/user/boards/{boardId}/posts` (multipart/form-data) ✅구현됨
```
title(필수·200자) / content(필수, 마크다운) / categoryId / isAnonymous
attachmentIds[](선업로드 연결·권장) / files[](이 요청에 함께 올리는 첨부) / draftId(선택)
→ 200 { "message": "...", "postId": 185 }        ← postId 로 상세 이동
→ 400 "제목은 필수입니다." 등 검증 3종 / 403 작성 권한 없음
```

### 게시글 수정 — `PUT /api/user/boards/{boardId}/posts/{postId}` (multipart/form-data) ✅구현됨
```
title/content 필수(등록과 동일 검증) + 첨부는 증분:
  deleteAttachmentIds[] = 화면에서 X 누른 기존 첨부의 id만
  attachmentIds[]       = 수정 중 새로 선업로드한 파일
  files[]               = 이 요청에 함께 올리는 첨부만  (유지할 첨부는 아무것도 안 보냄)
→ 200 { "message": "..." }                        ← 상세 객체는 안 옴. 수정 후 상세로 이동하며 GET 재호출
→ 403 수정 권한 없음 / 404 블라인드·삭제된 글
※ 본문에서 지운 인라인 이미지는 서버가 함께 삭제한다 (첨부 목록 파일은 deleteAttachmentIds 로만)
```

### 임시저장 5종 — ✅구현됨(2026-08-23, SPEC-A5 + PR #83 통합)
```
POST   .../drafts                { title?, content?, categoryId?, isAnonymous?, attachmentIds? }
                                 → { message, draftId } / 409 "최대 5개" (보관 상한 5, 게시판별)
PUT    .../drafts/{draftId}      같은 본문 → { message }        (저장 버튼 재클릭 = 덮어쓰기, 안 쌓임)
GET    .../drafts?limit=5        → { drafts: [{draftId, title, preview(20자), attachCnt, updatedAt}] }  ← count 없음, 개수는 length
GET    .../drafts/{draftId}      → 이어쓰기용 전체 본문 + attachments(일반 첨부만) + updatedAt
DELETE .../drafts/{draftId}      → 물리 삭제 (선업로드된 본문 이미지·첨부의 파일까지 함께 삭제)
※ 남의/없는 draftId 는 전부 404. 날짜는 목록·단건 모두 updatedAt(마지막 저장 시각)만.
```
- **초안 첨부도 선업로드로**: 파일은 `POST .../files`(§2)로 올리고, 저장 요청의 `attachmentIds` 에 넣으면 초안에 보존된다.
  본문에 `/api/user/files/{id}` 가 남아 있으면 attachmentIds 에 빠뜨려도 서버가 본문을 훑어 함께 보존한다(발행과 동일).
- **저장은 재조정(reconcile)이다**: 이번 저장의 `attachmentIds` ∪ 본문에 없는 기존 초안 첨부는 **서버가 파일까지 삭제**한다.
  화면에서 첨부를 X 로 지웠으면 다음 저장에서 그 id 를 빼기만 하면 된다(별도 삭제 호출 불필요).
- **초안에 묶인 파일은 24시간 정리 배치의 대상이 아니다** — 며칠 뒤 이어쓰기해도 이미지·첨부가 살아 있다.
  단 초안 자체를 지우면 그 순간 파일도 사라진다.
- 발행 시 `draftId` 를 넣으면 **초안의 첨부 전량**이 글로 이관되고 초안은 삭제된다. 발행 본문에서 지운 인라인 이미지는
  서버가 정리하지만, **일반 첨부는 전부 글에 남는다** — 발행 화면에서 첨부를 뺐다면 발행 전에 PUT .../drafts 재조정으로
  먼저 제거하고 발행할 것(발행 요청에는 첨부 삭제 수단이 없다).

### 그 외 확정 사항
- **상단 N개**: `GET .../posts/top/{num}` — 5 고정이 아니라 요청 개수만큼(1~50).
- **상세와 댓글 분리**: 상세(`GET .../posts/{postId}`)에는 `commentCount` 만 옴.
  댓글 본문은 `GET .../posts/{postId}/comments` 별도 호출 (블라인드·삭제 댓글은 서버가 제외, 익명/비밀댓글 마스킹도 서버 책임).
- **날짜 규칙**: 목록 = `created`(작성일)만 / 상세 = `created`+`updated` / 임시저장 = `updatedAt`만.
- **응답의 `fileUrl` 은 인증형 API 주소다** (2026-08-23 변경): 상세·초안·관리자 상세의 attachments 가 내려주는
  `fileUrl` 값이 물리 경로가 아니라 `/api/user/files/{attachmentId}` 로 바뀌었다. **이 값으로 fetch(+Authorization)**
  하면 된다(§2-1 blob 방식). 다운로드 시 `Content-Disposition: attachment; filename="원본명"` 으로 원본 파일명이 떨어진다.
  `originName` 은 표시용.
- **상세의 `attachments`**: 본문에 삽입된 인라인 이미지는 포함되지 않는다(첨부 목록용 파일만).
  목록의 `hasAttachment`(클립 아이콘)도 같은 기준이다.
- **에러 형식**: 전부 `{"message": "..."}` JSON. 미인증 401 / 권한부족 403.
