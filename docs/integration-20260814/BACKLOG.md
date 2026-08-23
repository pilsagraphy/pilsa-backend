# 기획안(1차 디자인 시안) 대비 백엔드 부족 기능 백로그

> 디자인 시안 PDF 전장(34판) + pilsa-frontend 코드를 대조해 정리. 2026-08-14, Claude 작성.
> 표기: 🔴 필수(기획 화면이 직접 요구) / 🟡 보완(있으면 화면 완성) / 🔵 정책 확정 필요(기획 질문)
> API 형식은 제안 초안이며, 착수 시 노션 명세에 확정본을 등록할 것.

---

## A. 게시판 관리 (관리자 > 커뮤니티 관리)

> ⚠️ **2026-08-14 2차 반영으로 아래 항목 중 다수가 이미 구현 완료됨.**
> 완료: A-1(게시판 CRUD·동적화), A-2 일부(댓글 관리는 미구현이나 조치 모듈 준비됨), G-1, G-2, G-4.
> H-1(알림)은 **부분 완료** — 웹 푸시·기기 등록부만 되어 있고 **발행과 알림함 API 는 담당자 과제**(H-1 항목 참고).
> 각 항목 머리에 상태를 표기했다. 미구현 항목만 팀원에게 배정하면 된다.

### A-1. ✅ **완료(2026-08-14)** 게시판 CRUD API — 시안 p4 "게시판 관리"
현재 게시판이 `BoardType` enum(1=공지, 2=자유, 3=정보)으로 **하드코딩**되어 있어
시안의 "새 게시판 생성" 버튼과 정면 충돌. DB에는 이번에 `boards.read_scope`/`write_level` 컬럼을
선반영해 두었으므로(#61), 정책을 DB 기반으로 전환하는 작업이 선행 과제.

| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| GET | /api/admin/boards | - | [{boardId, name(code), postCount, readScope, writeLevel, displayOrder}] | ADMIN |
| POST | /api/admin/boards | {name, readScope, writeLevel} | {boardId} | ADMIN |
| PUT | /api/admin/boards/{boardId} | {name?, readScope?, writeLevel?} | message | ADMIN |

- 열람 권한 드롭다운: 재학생(STUDENT)/동문(ALUMNI)/회원 전체(MEMBER)/전체 공개(ALL)
- 작성 권한 드롭다운: 일반회원(0)/관리 Lv.1~3(1~3)
- **선행 리팩터링**: BoardType의 adminWrite·defaultCategoryId·uploadDir을 boards 테이블(또는
  board 정책 컬럼)로 이전 → 신규 게시판이 코드 수정 없이 동작하도록. `/api/stu/{boardId}` 권한 검사도
  SecurityConfig 고정 규칙 대신 boards.read_scope/write_level 기반 동적 검사로 전환.
- 🔵 "전체 게시판 동기화" 버튼(시안 p5)의 의미 기획 확인 필요.

### A-2. 🔴 댓글 관리(전체 목록) API — 시안 p6 (담당 제안: 올리)
신고된 댓글이 아닌 **전체 댓글**을 게시판 필터/검색으로 조회·조치하는 화면. moderation 모듈 재사용으로 저비용.

| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| GET | /api/admin/comments | ?page&size&boardId&keyword | {totalPages,totalCount,comments:[{commentId,postId,boardId,boardName,authorName,content,created,state}]} | ADMIN |
| PATCH | /api/admin/comments/{id}/blind | {reasonId?,detail?} | message | ADMIN |
| PATCH | /api/admin/comments/{id}/restore | - | message | ADMIN |
| DELETE | /api/admin/comments/{id} | {reasonId?,detail?} | message (벌점 +2 자동) | ADMIN |
| POST | /api/admin/comments/bulk-delete | {commentIds,reasonId?,detail?} | BulkResult | ADMIN |

### A-3. 🟡 이전글/다음글 응답 확장 — 시안 p25 하단 내비게이션
현재 `prevPostApi`/`nextPostApi`가 post_id만 반환. 시안은 **카테고리 뱃지 + 제목 + 날짜** 표시.
→ BoardDetailResponse에 `{prev: {postId,title,categoryName,created}, next: {...}}` 구조 확장.

### A-4. ✅ 본문 인라인 이미지 업로드 — 시안 p21·23 리치 에디터 **(완료 2026-08-23)**
| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| POST | /api/user/boards/{boardId}/files | multipart(file, usage?) | {attachmentId,url,originName,fileSize,isImage,usageType,markdown} | 로그인+작성권한 |
| GET | /api/user/files/{fileId} | - | 파일 스트림 (열람 권한 검사) | 로그인 |
- 최종 경로는 `posts/images` 가 아니라 **`files`** — 본문 이미지와 첨부가 같은 API를 쓴다(명세 id 13 정본).
  용도 구분은 `attachments.usage_type`(inline/attachment) 이며 인라인 이미지는 첨부 목록에 노출되지 않는다.
- 고아 파일 정리: ①미연결 파일은 24시간 뒤 새벽 배치(04:50, `pending_upload_purge_hours`)
  ②수정 시 본문에서 사라진 인라인 이미지는 저장 시점에 정리.
- 본문 포맷은 **마크다운 확정**(2026-08-16 PM) — 서버는 그대로 저장·반환하고 새니타이즈는 렌더링하는 프론트 책임.
- 프론트 계약: [HANDOFF-editor.md](HANDOFF-editor.md) §2 (깃허브식 `![Uploading …]()` 자리표시자 흐름 포함).
- 동영상은 범위 밖 — 허용 확장자(policy_settings)에 추가하면 파일로는 올라가지만 본문 삽입(inline)은 이미지만 허용한다.

### A-5. ✅ 임시저장(글 저장하기) — 시안 p21 "저장 | 1" **(완료 2026-08-23, PR #83 + 선업로드 통합)**
📄 **상세 지시서: [SPEC-A5-drafts.md](SPEC-A5-drafts.md)** — 상단 "구현 완료 — 최종 확정 사항"이 정본.
구현: 6종 API + 발행 연동(draftId) + 초안 첨부 선업로드 보존 + 초안 삭제 시 파일 물리 삭제. 명세 id 15~19 active.

| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| POST | /api/user/boards/{boardId}/drafts | {title?,content?,categoryId?,isAnonymous?} | {draftId} | 로그인 |
| PUT | /api/user/boards/{boardId}/drafts/{draftId} | 위와 동일 | message | 본인 |
| GET | /api/user/boards/{boardId}/drafts | - | {count, drafts:[{draftId,title,updatedAt}]} | 본인 |
| GET | /api/user/boards/{boardId}/drafts/{draftId} | - | 초안 전체(이어쓰기용) | 본인 |
| DELETE | /api/user/boards/{boardId}/drafts/{draftId} | - | message | 본인 |
| POST(수정) | /api/user/boards/{boardId}/posts | form-data에 draftId? 추가 | 기존과 동일 | 기존과 동일 |

- posts.state 재사용 금지 → **별도 drafts 테이블**(목록/조회수/신고/제재 쿼리 오염 방지).
- drafts는 소프트삭제 대전제의 **예외**(세션성 데이터) → state 컬럼 없이 물리 삭제.
- ~~첨부파일은 attachments.post_id NOT NULL 제약상 초안 보존 불가 → 이번 범위 제외.~~
  → **해소(2026-08-23, A-4)**: post_id 가 nullable 이 되어 초안 첨부가 가능하다.
  초안은 선업로드한 `attachmentIds` 를 들고만 있고(파일은 이미 서버에 있다), 발행 시 그 id 를 글에 연결하면 된다.
  단 초안을 만든 뒤 24시간 안에 발행하지 않으면 미연결 파일 정리 배치가 파일을 지우므로,
  A-5 구현 시 **초안에 연결된 파일은 정리 대상에서 제외**하거나(초안 id 를 함께 보는 조건) 보존시간을 늘릴 것.

### A-6. 🟡 목록 정렬 옵션 확장 — FE가 `sort=liked`(좋아요순) 사용 예정. 현재 created/viewCount만 지원.

## B. 관리자 홈 대시보드 — 시안 p1 (담당 제안: 도이)

### B-1. 🔴 대시보드 통계/최근 목록 API
| 메서드 | 경로 | 응답 | 권한 |
|--------|------|------|------|
| GET | /api/admin/dashboard | {newMembers, pendingReports, newPosts, totalMembers, recentReports:[5], recentMembers:[5]} | ADMIN |
- 🔵 "신규"의 기간 기준(오늘? 7일?) 기획 확정 필요.
- recentReports: [{targetType,targetId,boardCode,preview,createdAt}] / recentMembers: [{memberType,loginId,name,joinedAt}]

## C. 마이페이지 — 시안 p33·34 (전체 부재, 대형) (담당 제안: 가람)
> remote 브랜치 `마이페이지-현황조회`가 존재(착수 흔적, PR 미제출). 이어받아 진행 권장.

### C-1. 🔴 프로필/활동 요약
| 메서드 | 경로 | 응답 | 권한 |
|--------|------|------|------|
| GET | /api/user/mypage | {loginId, name, joinedAt, postCount, commentCount, likedCount, semester:{posts,comments,receivedLikes}} | 본인 |
- "이번 학기" 기준일(3/1, 9/1) 상수화. 삭제(state != normal) 글 포함 여부 🔵 확정 필요.

### C-2. 🔴 내가 쓴 글 / 내가 쓴 댓글 / 좋아요 누른 글 목록
| 메서드 | 경로 | 요청 | 응답 |
|--------|------|------|------|
| GET | /api/user/mypage/posts | ?page&size&sort&categoryId&keyword | 목록(제목/좋아요/조회수/작성일) |
| GET | /api/user/mypage/comments | ?page&size&sort&keyword | 목록(제목=원글, 내용, 작성일) |
| GET | /api/user/mypage/likes | ?page&size&sort&categoryId&keyword | 목록(제목/좋아요/조회수/작성일) |

### C-3. 🔴 내 정보 수정 — "정보 수정" 버튼. 수정 가능 범위(비밀번호? 전화? 전공?) 🔵 기획 확정 필요.

## D. 일정 달력 — 시안 p9·10 (담당 제안: 예지)

### D-1. 🔴 시간(시:분) + 종일 일정 지원
시안의 등록/수정 폼은 년/월/일 + **시:분 드롭다운 + 종일 체크박스**. 현재 API는 'YYYY-MM-DD' 날짜만 수신.
- EventRequest: `startAt`/`endAt`을 'YYYY-MM-DD HH:mm'로 확장 + `allDay`(boolean, true면 00:00~23:59 저장)
- 조회 응답에도 시간/allDay 포함. events 테이블은 datetime이라 **DDL 불필요**.

### D-2. 🔴 일정 카테고리 — **담당자 과제**
`events.category` 가 varchar 자유 입력이라 표기가 갈리고 프론트가 필터·색상 구분을 만들 수 없다.
- **`event_categories` 테이블 + 기본 시드는 적용 완료** (PM). API 설계·구현이 과제
- 지시서: `docs/integration-20260814/HANDOFF-event-category-tasks.md`
- `categories` 테이블은 게시판 전용(board_id 소속)이라 일정에 재사용 금지

## E. 방명록 — 시안 사이드바(회원 영역) (부재) (담당 제안: 해미)
### E-1. 🔴 guestbook 테이블 + CRUD
| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| GET | /api/guestbook | ?page&size | 목록 | 로그인 |
| POST | /api/guestbook | {content, isAnonymous?} | message | 로그인 |
| DELETE | /api/guestbook/{id} | - | message | 본인/관리자 |
- 🔵 방명록 화면 시안 상세(익명/비밀 여부, 관리자 관리 탭 존재)가 이번 PDF에 없음 — 기획 확인 후 착수.
- 소프트삭제 대전제 적용(state 컬럼 포함) + 신고 대상 포함 여부 확정.

## F. 활동 사진(갤러리) — 시안 p18·19 (부재) (담당 제안: 해미 또는 신규 배정)
### F-1. 🔴 갤러리 테이블 + 조회/관리 API
| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| GET | /api/gallery | ?page&size | [{galleryId,title,tags,thumbnailUrl,images:[...]}] | 로그인 |
| POST | /api/admin/gallery | multipart(title,tags,files[]) | {galleryId} | ADMIN |
| PUT/DELETE | /api/admin/gallery/{id} | ... | message | ADMIN |
- 시안: 앨범 카드(제목 "필사그래피 MT" + 해시태그 "#바다 #날씨_최고 #2026") 형태.

## G. 신고/제재 보완

- G-1. ✅ **완료(2026-08-14)** 정지/차단 응답 구조화 — 로그인·세션 중 차단 모두 `{message, banType, bannedUntil}` JSON.
- G-2. ✅ **완료(2026-08-14)** 댓글 신고 목록 행에 원글 postId 포함 — ReportedItemResponse 확장.
- G-3. ✅ **종결(PM 확정)** 경고 단계는 **ban_policy 3단계**(1주/1달/영구)가 정답 — 시안 `N/5`는 오표기, **FE가 `/3`으로 수정**할 것.
- G-4. ✅ **완료(2026-08-14)** 이미 삭제된 대상 신고 409 거부 + 본인 글 신고 400 거부.

## H. 기타

- H-1. 🔶 **부분 완료 → 나머지는 담당자 과제(2026-08-16 PM 지시)**
  알림(헤더 종 아이콘). **완성**: notifications/notification_devices 테이블,
  웹 푸시 발송(VAPID·만료 기기 자동 정리), 수신 기기 등록/해제 API.
  **미구현(담당자 과제)**: **알림 발행 자체**(현재 알림이 하나도 생기지 않음 —
  `BoardServiceImpl.createComment` 에 TODO 자리만 있음)와 알림함 API(목록·읽음·삭제·미읽음수).
  → 과제 지시서 **`HANDOFF-notification-tasks.md`** (경로·요청/응답을 정해주지 않고 요구사항만 제시.
  담당자가 설계 → PM 확인 → 구현 → `api_endpoints` 25·26·27·28·29 를 직접 갱신).
- H-2. ⚪ 아이디 저장(로그인 체크박스) — FE 로컬스토리지 처리로 충분, 백엔드 불필요.
- H-3. 🔵 재학생 인증 절차(회원가입 승인 플로우) — 현재 가입 즉시 활성. 승인 대기 개념 유무 기획 확인.

---

## 우선순위 제안 (스프린트 관점)

| 순위 | 항목 | 근거 |
|------|------|------|
| 1 | C. 마이페이지 | 회원 화면 완성의 마지막 큰 조각, 착수 흔적 있음 |
| 2 | A-2. 댓글 관리 | 관리자 커뮤니티 관리 4메뉴 중 유일한 공백, moderation 재사용으로 저비용 |
| 3 | B-1. 대시보드 | 관리자 첫 화면, 조회만이라 리스크 낮음 |
| 4 | D-1. 일정 시간 | DDL 불필요, 소규모 |
| ~~5~~ | ~~A-4/A-5. 에디터 업로드·임시저장~~ | ✅ 전부 완료(2026-08-23) — A-4 선업로드 + A-5 임시저장(PR #83 통합) |
| 6 | A-1. 게시판 CRUD(+동적화) | 파급 범위 커서 설계 리뷰 선행 |
| 7 | E/F. 방명록·갤러리 | 신규 도메인, 기획 상세 확인 후 |
