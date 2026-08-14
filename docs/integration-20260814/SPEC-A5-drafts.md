# A-5. 임시저장(글 저장하기) 작업 지시서

> 대상 화면: 시안 p21·23 글쓰기 — "글 저장하기" 버튼 + 우측 상단 `저장 | 1` 카운터
> 담당 제안: 사라연 (board 도메인 담당) / 선행: 없음 (A-4 이미지 업로드와 병행 가능)
> 작성: 2026-08-14. 착수 전 아래 §5 기획 확정 항목부터 PM에게 확인할 것.

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
- **첨부파일은 이번 범위에서 제외**: attachments가 `post_id` NOT NULL FK라 초안에 붙일 수 없다.
  임시저장은 제목/본문/카테고리/익명여부만 보존하고, 파일 첨부는 발행 시점에 올린다.
  (본문 인라인 이미지는 A-4가 붙으면 URL이 content에 들어가므로 자동으로 함께 보존됨)
- boards 정책(allow_anonymous 등)은 저장 시점에 검증하지 말고 **발행 시점에만** 검증한다.
  초안 단계에서 게시판 정책이 바뀔 수 있기 때문.

---

## 2. 만들 기능 (API 6종)

| # | 메서드 | 경로 | 요청 | 응답 | 권한 |
|---|--------|------|------|------|------|
| 1 | POST | `/api/stu/{boardId}/drafts` | `{title?, content?, categoryId?, isAnonymous?}` | `{draftId}` | 로그인 + 해당 게시판 쓰기 권한 |
| 2 | PUT | `/api/stu/{boardId}/drafts/{draftId}` | 위와 동일 | `{message}` | 본인 |
| 3 | GET | `/api/stu/{boardId}/drafts` | - | `{count, drafts:[{draftId,title,updatedAt}]}` | 본인 |
| 4 | GET | `/api/stu/{boardId}/drafts/{draftId}` | - | `{draftId,title,content,categoryId,isAnonymous,updatedAt}` | 본인 |
| 5 | DELETE | `/api/stu/{boardId}/drafts/{draftId}` | - | `{message}` | 본인 |
| 6 | (기존 수정) POST | `/api/stu/{boardId}/posts` | form-data에 `draftId?` 추가 | 기존과 동일 | 기존과 동일 |

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

---

## 4. 후속(이번 범위 밖, 별도 티켓)
- 오래된 초안 정리 배치 (보관기간 경과분 삭제) — §5-3 확정 후
- 자동저장(주기적 PUT) — 프론트 주도. 백엔드는 2번 API로 이미 대응됨
- 첨부파일 초안 보존 — attachments 스키마 변경이 필요해 별건

---

## 5. PM 확정 필요 (팀원이 물어보기 전에 정해줄 것)

1. **보관 개수 상한**: 회원당 게시판별 몇 개까지? (제안: 게시판당 5개)
   → 시안의 `저장 | 1` 카운터는 상한이 있어야 의미가 있음.
2. **초안의 게시판 이동 허용 여부**: 자유게시판에서 쓰던 초안을 정보게시판으로 발행 가능?
   (제안: 불가 — board_id 고정. 허용하면 카테고리 정합성이 깨짐)
3. **보관 기간**: 무기한 vs N일 후 자동 삭제 (제안: 90일)
4. **관리자 열람 여부**: 발행 전 초안을 관리자가 볼 수 있어야 하나?
   (제안: 불가 — 개인 작업물, 신고·제재 대상도 아님)
