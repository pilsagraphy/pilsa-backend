# 2026-08-16 전체 재검토 — 게시판(리치 에디터)·알림·검토 필요 API 정리

> 배경: 프론트가 리치 에디터를 처음 다뤄 게시판 API 계약이 헷갈리는 상태였고,
> 알림은 웹앱(구글 플레이 TWA) 출시 시 푸시/배지가 가능한지 미확정이라 두 영역이 phase `검토 필요`로 남아 있었다.
> 이번 검토로 `검토 필요` 9건 중 8건을 해소했다 (남은 1건은 인라인 이미지 — 아래 §4).

---

## 1. `검토 필요` 9건 처분 결과

| id | API | 처분 |
|----|-----|------|
| 5 | POST 게시글 등록 | **2기 확정.** 어제 합의(postId 응답·검증 400·isPinned 제거) 코드 반영 완료 |
| 7 | PUT 게시글 수정 | **2기 확정.** 합의({message} 응답·증분 첨부·검증 400) 코드 반영 완료 |
| 13 | POST 인라인 이미지 | **유일하게 검토 필요 유지** — 본문 포맷(HTML)·XSS 새니타이즈·첨부 방식 통일(①) 확정 대기 |
| 25~29 | 알림(toast) 5종 | **2기 확정.** 인앱 알림 API는 현행 그대로 유효. 웹앱 푸시·배지는 별개 채널(3기 증축)이라 이 API에 영향 없음 → [PUSH-NOTIFICATION-GUIDE.md](PUSH-NOTIFICATION-GUIDE.md) |
| 39 | GET 관리자 게시글 상세 | **2기 확정.** 코드와 일치 확인. 첨부 예시를 실제 DTO 형태로 정정 |

phase 분포: 1기 8 / 2기 70 / 3기 5 / 검토 필요 1 (id=13).

## 2. 어제 합의 → 코드 반영 (이번에 수정한 것 4건, 컴파일 통과)

| 합의 | 수정 전 코드 | 수정 |
|------|------------|------|
| 등록 응답에 postId | message만 반환 (PK는 매퍼가 채우고 버림) | `BoardResponse`에 postId 추가(NON_NULL), 등록 응답에 포함 |
| 수정 응답은 {message}만 | 상세 객체(BoardDetailResponse) 전체 반환 | `{message}` 반환으로 변경 — 상세는 어차피 이동하며 GET 재호출 |
| title/content 필수 → 400 | 수정 DTO에 @NotBlank 없어 **빈 문자열이 DB에 그대로 반영** | `BoardUpdateRequest`에 @NotBlank 2개 추가 |
| isPinned 요청 필드 없음 | Swagger 설명이 isPinned를 요청 필드처럼 안내 (프론트 오도) | @Operation 문구 정정 |

이미 코드에 있던 것(수정 불필요): isPinned 필드 제거·카테고리 PINNED 서버 판정, 등록 검증(@NotBlank+@Size 200), 첨부 증분(deleteAttachmentIds 소프트삭제+files 추가), 카테고리 목록 토큰 분기, 일반회원 '중요' 강제 전송 방어.

## 3. 프론트용 리치 에디터 데이터 계약 (혼란 해소용 — 이대로 안내)

```
[글쓰기 화면에서 일어나는 일]

1. 본문 작성        → content 는 **마크다운 문자열** (HTML 아님 — PM 확정 2026-08-16)
2. 이미지 삽입      → 그 순간 POST .../posts/images 로 선업로드 → {url} 을 ![](url) 로 본문에 삽입
                      (에디터에 표시할 URL이 즉시 필요하므로 발행/임시저장 구분 없이 선업로드 — 이 API가 A-4)
3. 파일 첨부        → 발행 폼의 files[] 에 담아 두었다가 발행 multipart 에 함께 전송
4. 글 저장하기(임시) → POST .../drafts (2번째부터는 PUT .../drafts/{draftId} 덮어쓰기)
                      본문 속 이미지 URL은 마크다운 텍스트라 그대로 보존됨 (임시저장이어도 이미지는 이미 서버에 있음)
5. 발행             → POST .../posts (multipart) + draftId 포함 시 초안 자동 삭제
                      응답의 postId 로 상세 페이지 이동
6. 수정             → PUT .../posts/{postId} (multipart)
                      첨부는 증분: 지울 것만 deleteAttachmentIds, 새 것만 files. 응답은 message만
```

- 검증: title 필수·200자, content 필수 — 위반 시 400 `{"message"}` (등록·수정 동일)
- 상단 고정: isPinned 필드는 **없다**. 카테고리에서 '중요'를 고르면(관리자에게만 보임) 서버가 고정
- 서버는 content 마크다운을 그대로 저장·반환. 렌더링 시 프론트가 새니타이즈(마크다운 → HTML 변환 단계)

## 4. 결정 대기 (PM 확정 필요)

1. **첨부 방식 통일 (합의문의 ①)**: 게시글=발행 시 multipart `files` vs 초안=`attachmentIds`(선업로드)로 갈라져 있음.
   SPEC-A5 §6(attachments.draft_id 안)과 합의문의 attachmentIds 안도 서로 다름 — §6은 파일을 초안에 직접 귀속,
   합의안은 별도 업로드 후 id 연결. **인라인 이미지는 어차피 선업로드가 강제**되므로, 업로드 엔드포인트가 생기는 김에
   §6 + usage_type(§6-6)으로 한 번에 정리하는 안을 권장. 결정 나면 id 13·16·18 명세 확정 가능.
2. ~~임시저장 보관 상한 N~~ → **5개 확정** (2026-08-16). policy_settings.draft_max_count=5 등록 — 구현 시 하드코딩 금지, 이 값을 로드할 것.
3. ~~알림 발행 범위~~ → **확정 (2026-08-16)**: 내가 작성한 글에 달린 댓글(COMMENT) + 내가 작성한 댓글에 달린 대댓글(REPLY) **만** 발행한다.
   현재 코드가 정확히 이 동작이므로 변경 없음. REPORT_RESOLVED/SANCTION/NOTICE 는 발행하지 않는 것으로 확정.

## 5. 시안 대비 신규 발견 갭 (관리자 — 결정 대기)

| # | 시안 근거 (PM 코멘트) | 상태 |
|---|---|---|
| 1 | "블라인드·삭제만 모아보기" | **위치 정정(2026-08-16)**: 최초 보고에서 게시글 관리로 잘못 귀속 — PM 확인 결과 **신고 관리 소관**. 신고 목록에는 status 필터(pending/rejected/resolved)가 이미 있어 처리 상태 기준 모아보기는 가능. 대상 state(blind/deleted) 기준이 따로 필요하면 그때 파라미터 추가 |
| 2 | "신고관리 이동 시 회원 ID 전달" | **갭 확인됨(PM 확인)**: GET /api/admin/reports/posts·comments 에 작성자 필터 없음. 필요 시 `userId` 파라미터 추가(JOIN은 이미 있어 한 줄) — 착수 지시 대기 |
| 3 | 첨부 다운로드 파일명 | **해소(2026-08-16)**: 저장 방식을 UUID → **원본 파일명**(uploads/board-{boardId}/{postId}/원본명, 중복 시 "이름 (1).ext")으로 변경. 첨부 교체/삭제 시 물리 파일도 함께 지워 고아 파일 방지. attachments 0행 시점이라 마이그레이션 불필요 |

## 6. 웹앱 푸시·배지 결론 (상세: PUSH-NOTIFICATION-GUIDE.md)

- **TWA(안드로이드)에서 웹 푸시 → 네이티브 알림 표시 가능.** Firebase SDK 불필요(표준 VAPID).
- **배지**: 안드로이드는 알림이 떠 있는 동안 런처가 자동 표시(숫자 직접 제어 불가). iOS PWA 16.4+와 데스크톱은 setAppBadge로 숫자 제어 가능.
- 기존 toast API는 그대로 유지 — 푸시는 순수 증축(구독 테이블 + 구독 API + 발송 훅)이라 **3기로 미뤄도 마이그레이션 없음**.
- 프론트에 지금 알릴 것: linkUrl 상대경로 규칙 유지, unread-count 계약 유지, manifest/SW 뼈대는 미리 갖춰도 무방.

---

## 7. 2026-08-16 2차 반영 (PM 지시 이행)

| 항목 | 내용 |
|---|---|
| 본문 포맷 | **마크다운 확정** (HTML 아님). 명세 id 5·7·13 갱신 |
| 구글 캘린더 구독 | **구현 완료** — `GET /api/event/calendar.ics` (ICS 피드, PUBLIC). 프론트 [구독하기] = `calendar.google.com/calendar/render?cid={인코딩한 피드 URL}` 열기. 한 번 구독하면 이후 일정 등록/수정/삭제가 자동 반영(구글이 주기 재조회). OAuth·구글 API 불필요, 애플/아웃룩도 같은 URL 사용 가능 |
| 일정 카테고리 | **event_categories 테이블 신설**(정기모임/MT/행사/스터디/기타 시드) + `GET /api/event/categories` 구현 + 일정 등록/수정 시 카테고리 검증(400). DDL 은 CHECKLIST §4 기록 |
| 첨부 저장 방식 | UUID → **원본 파일명** (uploads/board-{boardId}/{postId}/원본명). 금지문자·경로이탈 새니타이즈, 같은 글 내 중복 이름은 " (1)" 부여. 첨부 삭제/교체 시 물리 파일도 삭제(고아 방지, 경로 이탈 가드 포함) |
| 임시저장 상한 | **5개 확정** — policy_settings.draft_max_count=5 |
| 알림 범위 | **COMMENT/REPLY 만 발행 확정** (현행 코드 그대로, 변경 없음) |
| 스웨거 | 구현 완료 API 전체에 @Tag(도메인 그룹)·@Operation(설명+요청/응답 예시) 부착 + swagger-ui 검색(filter)·정렬 설정 |
