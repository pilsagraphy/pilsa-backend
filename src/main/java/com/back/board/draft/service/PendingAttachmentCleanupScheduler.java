package com.back.board.draft.service;

import com.back.board.draft.mapper.DraftMapper;
import com.back.global.util.FileStorageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 업로드 대기 첨부 청소 배치 (일 1회 04:15).
 *
 * 선업로드된 뒤 초안/게시글에 끝내 귀속되지 못한 첨부(post_id·draft_id 둘 다 NULL)는
 * 디스크와 DB 를 계속 점유한다. 이런 "대기 고아"가 N시간(기본 24h) 넘게 방치되면 정리한다.
 *  - 예: 에디터에 이미지를 넣었다가 저장 없이 창을 닫은 경우, 첨부만 올리고 초안 저장을 안 한 경우.
 *
 * 순서: 행이 살아있을 때 파일 경로를 먼저 확보 → DB 행 물리 DELETE → 물리 파일 삭제.
 * (초안/게시글에 이미 묶인 첨부는 둘 중 하나가 NOT NULL 이라 대상에서 제외된다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingAttachmentCleanupScheduler {

    // 대기 상태 유예 시간 (이 시간 넘게 미귀속이면 정리)
    private static final int PENDING_TTL_HOURS = 24;

    private final DraftMapper draftMapper;
    private final FileStorageUtil fileStorageUtil;

    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void purgeExpiredPendingAttachments() {
        List<String> fileUrls = draftMapper.findExpiredPendingAttachmentUrls(PENDING_TTL_HOURS);
        if (fileUrls.isEmpty()) {
            return;
        }
        int deleted = draftMapper.deleteExpiredPendingAttachments(PENDING_TTL_HOURS);
        fileUrls.forEach(fileStorageUtil::delete);
        log.info("업로드 대기 첨부 청소 배치 - {}시간 초과 대기 고아 {}건 물리 삭제", PENDING_TTL_HOURS, deleted);
    }
}
