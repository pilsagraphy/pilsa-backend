package com.back.admin.moderation.revision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 글·댓글 본문 스냅샷.
 *
 * 소프트 삭제라 본문 자체는 남지만, 작성자가 신고당한 뒤 지워지기 전에 본문을 고쳐 버리면
 * 신고 당시 문장이 사라진다. 그래서 세 시점에 그때의 본문을 복사해 둔다 (2026-09-20 PM):
 *  - report     : 신고가 접수될 때 (신고자가 본 문장)
 *  - edit       : 작성자가 고치기 직전 (고치기 전 문장)
 *  - moderation : 관리자가 블라인드·삭제할 때 (조치 근거)
 *
 * 스냅샷은 실패해도 원래 동작(신고·수정·조치)을 막지 않는다 — 증적이 본 업무보다 앞설 수는 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentRevisionService {

    public static final String TRIGGER_REPORT = "report";
    public static final String TRIGGER_EDIT = "edit";
    public static final String TRIGGER_MODERATION = "moderation";

    private final ContentRevisionMapper contentRevisionMapper;

    @Transactional
    public void snapshot(String targetType, Long targetId, String trigger, Long savedBy) {
        try {
            if ("post".equals(targetType)) {
                contentRevisionMapper.snapshotPost(targetId, trigger, savedBy);
            } else if ("comment".equals(targetType)) {
                contentRevisionMapper.snapshotComment(targetId, trigger, savedBy);
            }
        } catch (RuntimeException e) {
            log.warn("[스냅샷] 저장 실패 - {} {} ({}): {}", targetType, targetId, trigger, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ContentRevisionResponse> findByPost(Long postId) {
        return contentRevisionMapper.findByPost(postId);
    }

    @Transactional(readOnly = true)
    public List<ContentRevisionResponse> findByPostComments(Long postId) {
        return contentRevisionMapper.findByPostComments(postId);
    }
}
