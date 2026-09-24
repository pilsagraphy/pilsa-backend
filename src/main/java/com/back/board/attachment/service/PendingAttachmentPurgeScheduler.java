package com.back.board.attachment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 선업로드 고아 파일 정리 배치 (일 1회 04:50 — 제재 캐시 04:00, 탈퇴 행 04:30, 알림 04:40 다음 순번).
 *
 * 리치 에디터는 파일을 고른 즉시 업로드하므로(글보다 파일이 먼저 존재한다),
 * 글을 쓰다가 그만두면 어디에도 연결되지 않은 파일이 디스크에 남는다. 그것만 지운다.
 *  - 대상: post_id·draft_id 둘 다 NULL 이고 보존시간이 지난 행
 *  - 글에 연결된 파일(post_id)이나 **임시저장에 묶인 파일(draft_id)은 절대 대상이 아니다** —
 *    초안은 며칠 뒤 이어 쓸 수 있으므로 보존시간과 무관하게 유지한다(초안 삭제·재조정 시점에 정리)
 *
 * 미연결 파일은 글에 속한 적이 없어 증적 가치가 없으므로 물리 삭제한다 —
 * 소프트삭제 대전제의 예외(초안 drafts 를 세션성 데이터로 본 것과 같은 논리).
 * 보존시간은 policy_settings.pending_upload_purge_hours (기본 24시간).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingAttachmentPurgeScheduler {

    private static final int DEFAULT_PURGE_HOURS = 24;

    private final AttachmentService attachmentService;

    @Scheduled(cron = "0 50 4 * * *")
    public void purgePendingUploads() {
        int hours = attachmentService.purgeHours(DEFAULT_PURGE_HOURS);
        int purged = attachmentService.purgeExpiredPending(hours);
        if (purged > 0) {
            log.info("선업로드 고아 파일 정리 배치 - {}시간 경과 {}건 삭제", hours, purged);
        }
    }
}
