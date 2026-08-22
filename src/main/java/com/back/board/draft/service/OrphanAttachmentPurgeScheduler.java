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
 * 업로드 대기(고아) 첨부 청소 배치 (일 1회 04:50 — 다른 새벽 배치들 뒤).
 *
 * 본문 이미지·첨부는 에디터에 넣는 순간 선업로드되므로(표시 URL 이 즉시 필요),
 * 저장/발행하지 않고 창을 닫으면 post_id·draft_id 가 둘 다 NULL 인 채 디스크에 남는다.
 * 보관 시간(policy_settings.draft_orphan_purge_hours, 기본 24h)이 지난 대기분을 물리 삭제한다.
 *
 * 절차(초안 삭제와 동일): 행이 사라지기 전에 file_url 을 먼저 확보 → DB 행 삭제 → 물리 파일 삭제.
 * 수치는 하드코딩하지 않고 policy_settings 에서 로드한다(팀 컨벤션).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanAttachmentPurgeScheduler {

    private static final String POLICY_PURGE_HOURS = "draft_orphan_purge_hours";
    private static final int DEFAULT_PURGE_HOURS = 24;

    private final DraftMapper draftMapper;
    private final FileStorageUtil fileStorageUtil;

    @Scheduled(cron = "0 50 4 * * *")
    @Transactional
    public void purgeOrphanAttachments() {
        int cutoffHours = parseHours(draftMapper.findPolicySetting(POLICY_PURGE_HOURS));

        // CASCADE 가 아니라 조건부 DELETE 라 행이 지워지기 전에 경로를 먼저 확보한다
        List<String> fileUrls = draftMapper.findOrphanAttachmentUrls(cutoffHours);
        if (fileUrls.isEmpty()) {
            return;
        }
        int deleted = draftMapper.deleteOrphanAttachments(cutoffHours);
        fileUrls.forEach(fileStorageUtil::delete);
        log.info("업로드 대기 첨부 청소 배치 - {}시간 경과 {}건 물리 삭제", cutoffHours, deleted);
    }

    private int parseHours(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return DEFAULT_PURGE_HOURS;
        }
    }
}
