# A-5 임시저장(Draft) — 확정 DDL (2026-08-17)

> 팀 컨벤션상 `.sql` 파일은 레포에 커밋하지 않는다. **아래 DDL을 qa_pilsa 에 수동 적용**하고
> 적용 결과를 `CHECKLIST.md` 의 "적용한 DDL" 섹션에 체크한다.
> MySQL 8.0 / `utf8mb4_0900_ai_ci` 기준. **drafts 생성 → attachments 변경 순서**로 실행.
>
> 이 문서는 `SPEC-A5-drafts.md` 의 초안을 **실제 구현 확정본**으로 대체한다.
> 초안 대비 확정된 변경점(§ 맨 아래 "초안과의 차이" 참고):
> 1. 보관 개수 상한을 **물리 슬롯(slot_no 1~5) + UNIQUE** 로 강제 (카운트 방식 → 슬롯 방식)
> 2. 리치 에디터 본문은 **HTML** (`<img src="/files/{attachment_id}">`) — 본문 이미지 재조정(reconcile) 대상
> 3. attachments 소유 제약은 **완화 CHECK(방식 A)** — "업로드 대기(둘 다 NULL)" 를 허용
> 4. `attachment_type enum('file','image')` 로 첨부/본문이미지 구분
> 5. `uploaded_by` 컬럼 추가 — 업로드 대기 행의 소유자 확인(하이재킹 차단) + 사용자별 용량/청소 기준

---

## 1. drafts 테이블 생성

```sql
CREATE TABLE `drafts` (
  `draft_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '임시저장 고유 번호',
  `user_id`      bigint       NOT NULL                COMMENT '작성자 (→users)',
  `slot_no`      tinyint unsigned NOT NULL            COMMENT '임시저장 슬롯 번호 (1~5)',
  `board_id`     bigint       NOT NULL                COMMENT '작성 중인 게시판 (→boards)',
  `category_id`  bigint       DEFAULT NULL            COMMENT '선택한 카테고리 (→categories, 미선택 가능)',
  `title`        varchar(200) DEFAULT NULL            COMMENT '제목 (작성 중이라 NULL 허용)',
  `content`      longtext     DEFAULT NULL            COMMENT '본문 HTML (작성 중이라 NULL 허용)',
  `is_anonymous` tinyint(1)   NOT NULL DEFAULT 0      COMMENT '익명 게시 체크 여부',
  `created_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`draft_id`),
  -- 유저당 슬롯 1~5 를 물리적으로 강제 → "최대 5개" 를 DB 레벨에서 보장 (경합에도 안전)
  UNIQUE KEY `uq_drafts_user_slot` (`user_id`,`slot_no`),
  -- 내 임시저장 최근순 목록 조회용
  KEY `idx_drafts_user_updated` (`user_id`,`updated_at`),
  CONSTRAINT `ck_drafts_slot` CHECK (`slot_no` BETWEEN 1 AND 5),
  CONSTRAINT `fk_drafts_user`     FOREIGN KEY (`user_id`)     REFERENCES `users` (`user_id`)           ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_drafts_board`    FOREIGN KEY (`board_id`)    REFERENCES `boards` (`board_id`)         ON UPDATE CASCADE,
  CONSTRAINT `fk_drafts_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`category_id`)  ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='글쓰기 임시저장 (발행 전 초안)';
```

### 설계 근거
- **소프트삭제 예외**: drafts 는 발행 전 개인 작업물(세션성 데이터)이라 `state` 없이 물리 DELETE.
  CLAUDE.md 의 "세션성 데이터" 예외에 해당.
- **slot_no + UNIQUE(user_id, slot_no)**: "회원당 최대 5개" 를 애플리케이션 카운트가 아니라 DB 제약으로 강제.
  덮어쓰기는 슬롯을 유지한 UPDATE, 신규 저장은 빈 슬롯(1~5)을 찾아 INSERT. 5개가 다 차면 INSERT 가
  UNIQUE 위반으로 실패 → 서비스가 409("임시저장은 최대 5개까지 가능합니다")로 변환.
- boards 정책(allow_anonymous 등)은 저장 시점에 검증하지 않고 **발행 시점(POST /posts)에만** 검증한다.
  초안 단계에서 게시판 정책이 바뀔 수 있기 때문.

---

## 2. attachments 테이블 수정 (방식 A — 완화 CHECK)

```sql
-- ① 초안/업로드 대기 첨부는 아직 게시글이 없으므로 post_id NULL 허용
ALTER TABLE `attachments`
  MODIFY COLUMN `post_id` bigint NULL COMMENT '게시글 고유 번호 (초안/업로드 대기 상태면 NULL)';

-- ② 초안 소유 컬럼. 초안이 지워지면 첨부 메타도 함께 정리(CASCADE)
ALTER TABLE `attachments`
  ADD COLUMN `draft_id` bigint NULL COMMENT '임시저장 고유 번호 (발행되면 NULL 로 비움)' AFTER `post_id`,
  ADD KEY `idx_attachments_draft` (`draft_id`),
  ADD CONSTRAINT `fk_attachments_draft` FOREIGN KEY (`draft_id`) REFERENCES `drafts` (`draft_id`)
      ON DELETE CASCADE ON UPDATE CASCADE;

-- ③ 첨부 유형: file=일반첨부(목록 노출) / image=에디터 본문삽입 이미지
ALTER TABLE `attachments`
  ADD COLUMN `attachment_type` enum('file','image') NOT NULL DEFAULT 'file'
      COMMENT 'file=일반첨부 / image=본문삽입 이미지' AFTER `file_type`;

-- ④ 업로드 대기 행의 소유자 (하이재킹 차단 + 사용자별 용량/청소 기준).
--    선업로드 시점엔 draft_id/post_id 가 아직 없어 소유자를 알 방법이 이것뿐이다.
ALTER TABLE `attachments`
  ADD COLUMN `uploaded_by` bigint NULL COMMENT '업로드한 회원 (→users, 대기 행 소유 확인용)' AFTER `attachment_type`,
  ADD KEY `idx_attachments_uploaded` (`uploaded_by`,`created_at`),
  ADD CONSTRAINT `fk_attachments_uploader` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`user_id`)
      ON DELETE SET NULL ON UPDATE CASCADE;

-- ⑤ 완화 XOR: "동시 소유(post_id·draft_id 둘 다 값)만 금지".
--    둘 다 NULL(업로드 대기)은 허용 → 리치 에디터가 draft 생성 전에 올린 본문 이미지를 담는다.
ALTER TABLE `attachments`
  ADD CONSTRAINT `ck_attachments_owner` CHECK (
      NOT (`post_id` IS NOT NULL AND `draft_id` IS NOT NULL)
  );

-- ⑥ created_at 이 없다면 추가 (업로드 대기 파일 청소 배치 기준). 이미 있으면 생략.
-- ALTER TABLE `attachments`
--   ADD COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업로드 시각';
```

> **주의**: 현행 attachments 에는 이미 `created_at`, `state` 컬럼이 존재한다(코드 확인).
> ⑥은 만약을 대비한 참고용이며, 이미 있으면 실행하지 않는다.
> `state` 컬럼은 게시글 첨부의 소프트삭제에만 쓰인다. 초안/대기 첨부는 세션성 데이터라
> reconcile·삭제·청소 시 **물리 DELETE** 한다(state 를 쓰지 않는다).

### 왜 이 순서/제약인가
- **② draft_id ON DELETE CASCADE**: 초안을 물리 삭제하면 초안에 묶인 첨부 메타도 자동 정리된다.
  단, **발행(publish)** 은 CASCADE 에 걸리면 안 되므로 반드시 "소유권 이전(UPDATE) → 초안 DELETE" 순서(§3-(6)).
- **⑤ 완화 CHECK**: 엄격 XOR(정확히 하나) 였다면 "업로드는 됐지만 아직 어느 초안에도 안 묶인" 본문 이미지를
  담을 수 없다. 리치 에디터는 draft 가 만들어지기 전에 이미지를 올리므로 "둘 다 NULL" 상태가 반드시 필요하다.

---

## 3. 롤백

```sql
ALTER TABLE `attachments` DROP CHECK `ck_attachments_owner`;
ALTER TABLE `attachments` DROP FOREIGN KEY `fk_attachments_uploader`;
ALTER TABLE `attachments` DROP KEY `idx_attachments_uploaded`, DROP COLUMN `uploaded_by`;
ALTER TABLE `attachments` DROP COLUMN `attachment_type`;
ALTER TABLE `attachments` DROP FOREIGN KEY `fk_attachments_draft`;
ALTER TABLE `attachments` DROP KEY `idx_attachments_draft`, DROP COLUMN `draft_id`;

DROP TABLE IF EXISTS `drafts`;

-- post_id NOT NULL 복구는 NULL(대기/초안) 행을 먼저 정리한 뒤 실행할 것
DELETE FROM `attachments` WHERE `post_id` IS NULL;
ALTER TABLE `attachments` MODIFY COLUMN `post_id` bigint NOT NULL COMMENT '게시글 고유 번호';
```

---

## 초안(SPEC-A5-drafts.md)과의 차이 — 리뷰 포인트
| 항목 | 초안 | 확정본(이 문서) | 이유 |
|------|------|-----------------|------|
| 보관 상한 | 앱에서 count 검사 | `slot_no 1~5` + UNIQUE 로 DB 강제 | 경합/직접호출에도 5개 보장, 시안의 `저장 \| n` 슬롯 개념과 일치 |
| 본문 형식 | 마크다운 | **HTML**(리치 에디터) | 본 작업 지시가 HTML `<img>` 삽입 기준. 추출기는 마크다운도 관용 처리 |
| 소유 제약 | 엄격 XOR | **완화 CHECK(방식 A)** | draft 생성 전 올린 본문 이미지(둘 다 NULL)를 허용해야 함 |
| 유형 구분 | usage_type varchar | `attachment_type enum('file','image')` | 첨부 목록/본문 이미지 필터를 enum 으로 명확화 |
| 대기행 소유 | 없음 | `uploaded_by` 추가 | 대기 첨부 하이재킹 차단 + 용량/청소 기준 |
