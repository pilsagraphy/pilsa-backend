package com.back.student.notice.service;

import com.back.student.notice.dto.*;

import java.util.List;

public interface NoticeService {
    // 공지사항 전체 조회
    // 전체 조회 기능을 수행하고, 목록과 페이지 수를 묶은 NoticePageResponse를 반환
    NoticePageResponse getNoticeList(int page, int size, String keyword, String sort);
    // 메인 화면용 중요 공지 최신 5개 조회
    List<NoticeTop5Response> getTop5Notices();
    // 공지사항 단일글 조회
    NoticeDetailResponse getNoticeDetail(Long postId);
    // 좋아요 기능
    NoticeResponse toggleNoticeLike(Long postId);
    // 공지사항 등록 수정 삭제
    NoticeResponse createNotice(NoticeRequest request);
    NoticeResponse updateNotice(Long postId, NoticeUpdateRequest request);
    NoticeResponse deleteNotice(Long postId);
}