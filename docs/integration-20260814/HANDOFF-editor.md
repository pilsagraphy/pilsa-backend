# [전달] 게시글 리치 에디터 — 프론트 API 계약 (2026-08-16 확정)

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
3. 이미지 삽입       그 순간 선업로드(§2) → 받은 url 을 ![](url) 로 본문에 삽입
4. 파일 첨부         발행 폼의 files[] 에 쌓아 두기 (발행 때 함께 전송)
5. 글 저장하기(임시)  POST .../drafts → draftId 보관. 2번째부터는 PUT .../drafts/{draftId} 덮어쓰기
6. 발행              POST .../posts (multipart) — draftId 를 넣으면 그 초안은 서버가 자동 삭제
                     → 응답의 postId 로 상세 페이지 이동
```

## 2. 선업로드가 뭐고 왜 필요한가

이미지를 에디터에 넣는 순간 **화면에 보여줄 URL이 즉시 필요**하다. 발행 시점에 올리는 방식으로는
에디터가 이미지를 표시할 방법이 없으므로, 삽입 즉시 업로드하고 URL을 받아 본문 텍스트에 심는다.

- 담당 API: `POST /api/user/boards/{boardId}/posts/images` → `{"url":"/uploads/..."}`
  ⚠️ **아직 미구현(planned, 백로그 A-4)** — 구현 전까지 에디터의 이미지 버튼은 비활성 권장.
- **임시저장이어도 동일하다.** 초안에는 `![](url)` 텍스트만 저장되고 이미지 파일은 이미 서버에 있다.
- 첨부파일(pdf 등)은 선업로드가 아니라 발행 multipart 의 `files[]` — 화면에 미리 보여줄 필요가 없어서다.

## 3. API 계약 요약

### 게시글 등록 — `POST /api/user/boards/{boardId}/posts` (multipart/form-data) ✅구현됨
```
title(필수·200자) / content(필수, 마크다운) / categoryId / isAnonymous / files[] / draftId(선택)
→ 200 { "message": "...", "postId": 185 }        ← postId 로 상세 이동
→ 400 "제목은 필수입니다." 등 검증 3종 / 403 작성 권한 없음
```

### 게시글 수정 — `PUT /api/user/boards/{boardId}/posts/{postId}` (multipart/form-data) ✅구현됨
```
title/content 필수(등록과 동일 검증) + 첨부는 증분:
  deleteAttachmentIds[] = 화면에서 X 누른 기존 첨부의 id만
  files[]               = 새로 추가한 파일만        (유지할 첨부는 아무것도 안 보냄)
→ 200 { "message": "..." }                        ← 상세 객체는 안 옴. 수정 후 상세로 이동하며 GET 재호출
→ 403 수정 권한 없음 / 404 블라인드·삭제된 글
```

### 임시저장 5종 — ⚠️ 전부 미구현(planned, SPEC-A5). 계약만 확정
```
POST   .../drafts                { title?, content?, categoryId?, isAnonymous?, attachmentIds? }
                                 → { message, draftId } / 409 "최대 5개" (보관 상한 5 확정)
PUT    .../drafts/{draftId}      같은 본문 → { message }        (저장 버튼 재클릭 = 덮어쓰기, 안 쌓임)
GET    .../drafts?limit=5        → { drafts: [{draftId, title, preview(20자), updatedAt}] }  ← count 없음, 개수는 length
GET    .../drafts/{draftId}      → 이어쓰기용 전체 본문 + updatedAt
DELETE .../drafts/{draftId}      → 물리 삭제
※ 남의/없는 draftId 는 전부 404. 날짜는 목록·단건 모두 updatedAt(마지막 저장 시각)만.
```

### 그 외 확정 사항
- **상단 N개**: `GET .../posts/top/{num}` — 5 고정이 아니라 요청 개수만큼(1~50).
- **상세와 댓글 분리**: 상세(`GET .../posts/{postId}`)에는 `commentCount` 만 옴.
  댓글 본문은 `GET .../posts/{postId}/comments` 별도 호출 (블라인드·삭제 댓글은 서버가 제외, 익명/비밀댓글 마스킹도 서버 책임).
- **날짜 규칙**: 목록 = `created`(작성일)만 / 상세 = `created`+`updated` / 임시저장 = `updatedAt`만.
- **첨부 파일명**: 서버가 원본 파일명 그대로 저장하므로(`uploads/board-{id}/{postId}/원본명.pdf`)
  `fileUrl` 로 바로 다운로드해도 파일명이 원본으로 떨어진다. 목록의 `originName` 은 표시용.
- **에러 형식**: 전부 `{"message": "..."}` JSON. 미인증 401 / 권한부족 403.
