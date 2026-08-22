# A-5. 임시저장(글 저장하기) 작업 지시서

> 대상 화면: 시안 p21·23 글쓰기 — "글 저장하기" 버튼 + 우측 상단 `저장 | 1` 카운터
> 담당 제안: 사라연 (board 도메인 담당) / 선행: 없음 (A-4 이미지 업로드와 병행 가능)
> 작성: 2026-08-14. 착수 전 아래 §5 기획 확정 항목부터 PM에게 확인할 것.

---

## ✅ 구현 완료 (2026-08-22) — 최종 확정 사항

`com.back.board.draft` 패키지로 구현. DDL 은 CHECKLIST.md [2026-08-22] 항목에 기록(레포 .sql 미커밋 컨벤션).

- **상한 강제 방식**: PM 지시로 "**DB에서부터 5개 제한**" 채택 → 원안(§2 애플리케이션 count 검사)이 아니라
  `drafts.slot_no`(1~5) + `UNIQUE(user_id, board_id, slot_no)` 로 물리 강제. 서비스는 `policy_settings.draft_max_count`
  로 빈 슬롯을 1..N 탐색하고, 경합으로 슬롯이 겹치면 duplicate-key 를 **409** 로 변환한다.
  상한 범위는 CHECKLIST 의 `draft_max_count` 설명("회원당 **게시판별**")대로 **게시판별 5개**로 확정.
- **본문/첨부**: 본문은 마크다운 확정(2026-08-16)이라 원안 §3 의 "HTML 파싱 재조정"은 채택하지 않고,
  프론트가 보내는 **`attachmentIds` 목록을 소유의 정본**으로 삼아 재조정한다. (§6-1 방식 A + 완화 CHECK)
- **첨부 CHECK**: 완화형(`NOT(post_id IS NOT NULL AND draft_id IS NOT NULL)`) 채택 —
  선업로드 대기(둘 다 NULL)를 허용하기 위함(엄격 XOR 아님). `attachments` 에 `attachment_type`('file'/'image'),
  `uploaded_by`(대기분 소유 검증) 추가.
- **선업로드 엔드포인트**: `POST .../posts/images`(본문 이미지) + `POST .../posts/attachments`(첨부) — 둘 다 `{attachmentId, url, ...}` 반환.
- **발행 연동**: `POST .../posts` 에 `draftId` form 필드. 순서 엄수(UPDATE 이관 → DELETE 초안).
- **청소 배치**: `OrphanAttachmentPurgeScheduler`(04:50) — 대기 첨부를 `draft_orphan_purge_hours`(기본 24h) 경과 시 물리 삭제.

> 아래 원안 §1·§2 의 "slot_no 없음 / count 검사" 서술은 착수 전 초안이며, 최종 구현은 위 확정 사항을 따른다.

---

## 1. DB 설계 (팀원이 DDL 작성 → PM 승인 후 qa_pilsa 수동 적용)

**posts 테이블 재사용 금지.** `posts.state='draft'` 방식은 목록/조회수/신고/제재/관리자 화면 쿼리
전부에 예외 조건이 붙어 오염되므로, 별도 테이블로 분리한다.

```sql
CREATE TABLE `drafts` (
  `draft_id`    bigint       NOT NULL AUTO_INCREMENT COMMENT '임시저장 고유 번호',
  `user_id`     bigint       NOT NULL COMMENT '작성자 (→users)',
  `board_id`    bigint       NOT NULL COMMENT '작성 중인 게시판 (→boards)',
  `category_id` bigint       DEFAULT NULL COMMENT '선택한 카테고리 (→categories, 미선택 가능)',
  `title`       varchar(200) DEFAULT NULL COMMENT '제목 (작성 중이라 NULL 허용)',
  `content`     longtext     COMMENT '본문 (작성 중이라 NULL 허용)',
  `is_anonymous` tinyint(1)  NOT NULL DEFAULT '0' COMMENT '익명 게시 체크 여부',
  `created_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`draft_id`),
  KEY `idx_drafts_user_board` (`user_id`,`board_id`,`updated_at`),
  CONSTRAINT `fk_drafts_user`     FOREIGN KEY (`user_id`)     REFERENCES `users` (`user_id`)      ON UPDATE CASCADE,
  CONSTRAINT `fk_drafts_board`    FOREIGN KEY (`board_id`)    REFERENCES `boards` (`board_id`)    ON UPDATE CASCADE,
  CONSTRAINT `fk_drafts_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`category_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='글쓰기 임시저장 (발행 전 초안)';
```

### 설계 근거 (리뷰 포인트)
- **소프트삭제 대전제의 예외**: drafts는 발행 전 개인 작업물이라 감사·증적 대상이 아니다.
  `state` 컬럼 없이 물리 DELETE를 쓴다 (CLAUDE.md의 "세션성 데이터" 예외에 해당).
  → 팀원이 습관적으로 state를 넣어오면 반려할 것.
- **첨부파일**: attachments가 `post_id` NOT NULL FK라 그대로는 초안에 붙일 수 없다.
  → **§6의 attachments 수정안을 함께 적용하면 초안 첨부까지 가능**하다. PM이 §6 적용 여부를 먼저 결정할 것.
  (미적용 시: 임시저장은 제목/본문/카테고리/익명여부만 보존, 파일은 발행 시점에 업로드)
- boards 정책(allow_anonymous 등)은 저장 시점에 검증하지 말고 **발행 시점에만** 검증한다.
  초안 단계에서 게시판 정책이 바뀔 수 있기 때문.

---

## 2. 만들 기능 (API 6종)

| # | 메서드 | 경로 | 요청 | 응답 | 권한 |
|---|--------|------|------|------|------|
| 1 | POST | `/api/user/boards/{boardId}/drafts` | `{title?, content?, categoryId?, isAnonymous?}` | `{draftId}` | 로그인 + 해당 게시판 쓰기 권한 |
| 2 | PUT | `/api/user/boards/{boardId}/drafts/{draftId}` | 위와 동일 | `{message}` | 본인 |
| 3 | GET | `/api/user/boards/{boardId}/drafts` | - | `{count, drafts:[{draftId,title,updatedAt}]}` | 본인 |
| 4 | GET | `/api/user/boards/{boardId}/drafts/{draftId}` | - | `{draftId,title,content,categoryId,isAnonymous,updatedAt}` | 본인 |
| 5 | DELETE | `/api/user/boards/{boardId}/drafts/{draftId}` | - | `{message}` | 본인 |
| 6 | (기존 수정) POST | `/api/user/boards/{boardId}/posts` | form-data에 `draftId?` 추가 | 기존과 동일 | 기존과 동일 |

**초안 3개에서 늘어난 이유**
- **2번(덮어쓰기)**: 저장 버튼을 두 번 누르면 초안이 계속 쌓이면 안 된다. 프론트가 draftId를 들고 있으면 PUT.
- **4번(단건 불러오기)**: 목록만 있으면 "이어서 쓰기"가 불가능. 시안의 `저장 | 1`을 눌러 복원하는 동선의 핵심.
- **6번(발행 연동)**: 발행에 성공하면 그 초안은 사라져야 한다. `draftId`가 오면 posts INSERT와 **같은 트랜잭션**에서 draft를 삭제. (없는 draftId/남의 draftId면 무시하고 발행은 성공시킬 것 — 발행을 막을 이유가 없음)

### 비즈니스 규칙
- 본인 것만 접근. 남의 draftId면 **404**(403이 아니라 404 — 존재 여부 노출 방지).
- `title`과 `content`가 **둘 다 비면 400** ("저장할 내용이 없습니다").
- 게시판 쓰기 권한 검사는 기존 `BoardPolicyService.requireWritable(boardId)` 재사용. 새로 만들지 말 것.
- 보관 개수 상한(§5-1) 초과 시 **409**로 막고, 프론트가 "오래된 임시저장을 지워주세요" 안내.
- `{boardId}`와 draft의 board_id가 다르면 404 (경로 위조 방지).

### 패키지 위치
`com.back.board.draft` (controller/dto/mapper/service) — board 도메인 하위.
매퍼 XML은 `src/main/resources/mapper/board/DraftMapper.xml`.
> 새 최상위 패키지를 만들지 말 것. drafts는 게시판 글쓰기의 일부다.

---

## 3. 수용 기준 (이걸 통과해야 PR 승인)

- [ ] 저장 → 목록에 1건, `count=1` (시안의 `저장 | 1` 대응)
- [ ] 같은 초안을 PUT으로 3번 수정해도 목록은 여전히 1건, updatedAt만 갱신
- [ ] 단건 조회로 제목·본문·카테고리·익명여부가 저장 당시 그대로 복원
- [ ] 초안 불러와 발행(`draftId` 포함) → 게시글 등록 성공 + 해당 초안 삭제됨 + 목록 0건
- [ ] 발행 도중 실패(예: 잘못된 요청)하면 초안은 **남아 있어야** 함 (트랜잭션 롤백 확인)
- [ ] 다른 계정의 draftId로 GET/PUT/DELETE → 전부 404
- [ ] 제목·본문 모두 빈 저장 → 400
- [ ] 공지사항(쓰기 권한 없는 게시판)에 일반 회원이 초안 저장 시도 → 403

### §6 적용 시 추가
- [ ] 초안에 파일 2개 첨부 → 발행 → 게시글 상세에 첨부 2개 그대로 노출 (재업로드 없이)
- [ ] 발행 후 `attachments`의 해당 행이 `post_id=값, draft_id=NULL`인지 DB로 확인
- [ ] 첨부 있는 초안을 삭제 → attachments 행 삭제 + **물리 파일도 삭제**됨
- [ ] 학생 목록/상세 조회에 초안 첨부(post_id NULL)가 절대 섞이지 않음

---

## 4. 후속(이번 범위 밖, 별도 티켓)
- 오래된 초안 정리 배치 (보관기간 경과분 삭제) — §5-3 확정 후
- 자동저장(주기적 PUT) — 프론트 주도. 백엔드는 2번 API로 이미 대응됨

---

## 5. PM 확정 필요 (팀원이 물어보기 전에 정해줄 것)

1. **보관 개수 상한**: 회원당 게시판별 몇 개까지? (제안: 게시판당 5개)
   → 시안의 `저장 | 1` 카운터는 상한이 있어야 의미가 있음.
2. **초안의 게시판 이동 허용 여부**: 자유게시판에서 쓰던 초안을 정보게시판으로 발행 가능?
   (제안: 불가 — board_id 고정. 허용하면 카테고리 정합성이 깨짐)
3. **보관 기간**: 무기한 vs N일 후 자동 삭제 (제안: 90일)
4. **관리자 열람 여부**: 발행 전 초안을 관리자가 볼 수 있어야 하나?
   (제안: 불가 — 개인 작업물, 신고·제재 대상도 아님)
5. **§6 첨부파일 수정안 적용 여부** + 적용 시 **회원당 초안 첨부 총량 상한** (제안: 적용 / 회원당 50MB)

---

# 6. attachments 테이블 수정안 (초안 첨부 지원)

> 현재 `attachments.post_id`가 NOT NULL이라 "게시글이 없는 파일"을 못 담는다.
> **2026-08-14 기준 attachments는 0행** → 마이그레이션·백필 부담 없이 지금이 바꾸기 가장 좋은 시점.

## 6-1. 설계안 비교

| 안 | 방식 | 장점 | 단점 | 판정 |
|----|------|------|------|------|
| **A** | `post_id` NULL 허용 + `draft_id` 컬럼 추가 (둘 중 하나만 채움) | 발행 시 **UPDATE 한 줄로 소유권 이전**(파일 재업로드·행 복사 없음). FK 무결성 유지. 조회 코드 무영향 | 컬럼 2개 중 하나만 유효 → CHECK 제약 필요 | ✅ **채택** |
| B | `owner_type`/`owner_id` 다형성 (moderation_log 방식) | 나중에 방명록·갤러리 첨부도 같은 테이블로 수용 | **FK 못 검 → 고아 행이 DB 차원에서 안 막힘.** 파일은 실물 자원이라 정합성이 로그성 테이블보다 중요 | ❌ |
| C | `draft_attachments` 별도 테이블 | 기존 테이블 무손상 | 발행 시 행 복사+삭제, 업로드/조회/삭제 코드가 **2벌**로 갈라짐 | ❌ |

## 6-2. DDL (팀원 작성 → PM 승인 → 수동 적용. drafts 테이블 생성 **이후** 실행)

```sql
-- ① 초안 첨부는 아직 게시글이 없으므로 post_id NULL 허용
ALTER TABLE `attachments`
  MODIFY COLUMN `post_id` bigint NULL COMMENT '게시글 고유 번호 (초안 첨부 상태면 NULL)';

-- ② 초안 소유 컬럼 추가. 초안이 지워지면 첨부 메타도 함께 정리(CASCADE)
ALTER TABLE `attachments`
  ADD COLUMN `draft_id` bigint NULL COMMENT '임시저장 고유 번호 (발행되면 NULL로 비움)' AFTER `post_id`,
  ADD KEY `idx_attachments_draft` (`draft_id`),
  ADD CONSTRAINT `fk_attachments_draft` FOREIGN KEY (`draft_id`) REFERENCES `drafts` (`draft_id`)
      ON DELETE CASCADE ON UPDATE CASCADE;

-- ③ 소유자는 반드시 정확히 하나 (둘 다 NULL=고아 / 둘 다 값=이중소속 → 양쪽 다 차단)
ALTER TABLE `attachments`
  ADD CONSTRAINT `ck_attachments_owner` CHECK (
       (`post_id` IS NOT NULL AND `draft_id` IS NULL)
    OR (`post_id` IS NULL     AND `draft_id` IS NOT NULL)
  );
```

**롤백**
```sql
ALTER TABLE `attachments` DROP CHECK `ck_attachments_owner`;
ALTER TABLE `attachments` DROP FOREIGN KEY `fk_attachments_draft`;
ALTER TABLE `attachments` DROP KEY `idx_attachments_draft`, DROP COLUMN `draft_id`;
-- post_id NOT NULL 복구는 NULL 행을 먼저 정리한 뒤에 실행할 것
DELETE FROM `attachments` WHERE `post_id` IS NULL;
ALTER TABLE `attachments` MODIFY COLUMN `post_id` bigint NOT NULL COMMENT '게시글 고유 번호';
```

## 6-3. 발행 트랜잭션 — 순서를 틀리면 파일이 사라진다 ⚠️

```sql
-- ① 소유권 이전 먼저 (draft_id를 비워 CASCADE 대상에서 제외시킨다)
UPDATE attachments SET post_id = #{postId}, draft_id = NULL WHERE draft_id = #{draftId};

-- ② 그 다음 초안 삭제
DELETE FROM drafts WHERE draft_id = #{draftId} AND user_id = #{userId};
```

> **①과 ②를 바꾸면** `ON DELETE CASCADE`가 첨부 행을 통째로 지워서, 방금 발행한 글의 첨부가
> 전부 없어진다. 코드 리뷰에서 이 순서를 최우선으로 확인할 것. 반드시 같은 트랜잭션.

## 6-4. 물리 파일 처리

- **파일 이동 불필요.** 저장 경로는 업로드 시점에 확정되고, 발행은 DB 소유자만 바꾼다.
  (`FileStorageUtil`은 그대로 사용. 초안 업로드분은 `uploads/drafts/` 같은 별도 디렉터리를 쓰지 말 것 —
  발행 후 경로와 실제 소속이 어긋나 헷갈린다)
- **초안 삭제 시 물리 파일 삭제 필수.** DB는 CASCADE로 지워지지만 디스크 파일은 남는다.
  → 삭제 전 `SELECT file_url FROM attachments WHERE draft_id = ?`로 목록을 먼저 확보한 뒤
  DB 삭제 → 파일 삭제 순으로 처리. 보관기간 정리 배치도 동일한 절차를 탈 것.
- **용량 상한 필요**(§5-5): 초안은 발행되지 않아도 디스크를 계속 점유한다.
  회원당 초안 첨부 총량을 검사해 초과 시 413/409 반환.

## 6-5. 기존 코드 영향도 (전부 무영향 — 확인 완료)

| 코드 | 영향 |
|------|------|
| `BoardMapper.insertAttachment` | 항상 post_id를 넣으므로 그대로 동작. 초안용 insert만 신규 추가 |
| `BoardMapper.findAttachmentsByPostId` | `WHERE post_id = #{postId}` → draft 행(post_id NULL)은 자연히 제외됨 |
| `AdminPostMapper.findAttachments` | 위와 동일 |
| 첨부 다운로드(`/uploads/**` 정적) | 경로 불변이라 무영향 |

> 참고: `fk_attachments_post`의 `ON DELETE CASCADE`는 게시글이 전면 소프트삭제로 바뀐 뒤
> **발동할 일이 없는 죽은 제약**이다. 이번에 굳이 건드릴 필요는 없으나, 물리삭제가 없다는 전제를
> 팀원이 오해하지 않도록 리뷰 때 짚어줄 것.

## 6-6. A-4(본문 인라인 이미지)와의 관계 — 선택 사항

인라인 이미지도 "발행 전에 업로드되는 파일"이라 같은 문제를 겪는다. 한 테이블로 통합하려면:

```sql
ALTER TABLE `attachments`
  ADD COLUMN `usage_type` varchar(20) NOT NULL DEFAULT 'attachment'
    COMMENT 'attachment=첨부목록 노출 / inline=본문 삽입 이미지' AFTER `file_type`;
```
- 상세 화면 첨부 목록은 `WHERE usage_type='attachment'`로 필터 (시안 p25의 `[붙임1] …` 행)
- 인라인 이미지도 DB에 기록되므로 **고아 파일 정리를 한 곳에서** 처리 가능
- 컬럼명은 `usage`가 아니라 `usage_type` — `USAGE`는 MySQL 예약어라 백틱 없이는 파싱 오류

> A-4를 나중에 할 거면 이 컬럼은 그때 추가해도 된다. 다만 **A-4와 A-5를 같은 사람이 함께 맡으면**
> 지금 한 번에 넣는 편이 DDL 두 번 치는 것보다 낫다.
