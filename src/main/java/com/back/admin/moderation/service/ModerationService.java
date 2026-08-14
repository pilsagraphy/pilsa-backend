package com.back.admin.moderation.service;

// 게시글/댓글에 대한 공통 조치(블라인드/복원/삭제) 로직.
// 게시글 관리·신고 관리 두 페이지가 공유한다.
// targetType 은 "post" 또는 "comment".
public interface ModerationService {

    // 블라인드: state=blind + 조치이력 기록 (주의 포인트 없음)
    void blind(String targetType, Long targetId, Long actorId, Long reasonId, String detail);

    // 복원(공개/반려): state=normal + 조치이력 기록 + 관련 주의 포인트 회수(void)
    // 반환: 생성된 moderation_log action_id (이미 normal이라 no-op이면 null)
    Long restore(String targetType, Long targetId, Long actorId);

    // 소프트 삭제: state=deleted + 조치이력 기록 + 작성자 주의 포인트 적립(+2)
    //             + 누적 결과에 따른 경고/정지 에스컬레이션
    // 반환: 생성된 moderation_log action_id (이미 deleted라 no-op이면 null)
    Long softDelete(String targetType, Long targetId, Long actorId, Long reasonId, String detail);

    // 대상의 현재 표시 상태(normal/blind/deleted) 조회. 없으면 null.
    String currentState(String targetType, Long targetId);
}
