package com.back.admin.post.service;

import com.back.admin.moderation.service.ModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_POST;

// 게시글 1건 조치를 "독립 트랜잭션"으로 실행하는 컴포넌트.
// 일괄 처리 시 한 건이 실패해도 다른 건에 영향이 없도록 REQUIRES_NEW 로 분리한다.
// (별도 빈이라 프록시를 타므로 self-invocation 문제도 없음)
@Component
@RequiredArgsConstructor
public class PostBulkExecutor {

    private final ModerationService moderationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deletePost(Long postId, Long adminId, Long reasonId, String detail) {
        moderationService.softDelete(TARGET_POST, postId, adminId, reasonId, detail);
    }
}
