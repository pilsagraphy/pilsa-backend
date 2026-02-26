package com.back.student.notice.controller;

import com.back.student.notice.dto.*;
import com.back.student.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    // 공지사항 전체 조회
    @GetMapping("/api/stu/notices")
    public ResponseEntity<NoticePageResponse> getAllNotices(
            // 프론트에서 받는 것
            @RequestParam(value = "page", defaultValue = "1") int page, // 페이지 번호
            @RequestParam(value = "size", defaultValue = "10") int size, // 한페이지당 보여줄 개수
            @RequestParam(value = "keyword", required = false) String keyword, // 검색어
            @RequestParam(value = "sort", defaultValue = "created") String sort) { // 정렬 기준 기본은 최신순
        log.info("공지사항 목록 조회 요청 시작 - page: {}, size: {}, keyword: {}, sort: {}", page, size, keyword, sort);
        // Service를 통해 전체 페이지 수와 목록이 담긴 응답 객체 조회
        NoticePageResponse response = noticeService.getNoticeList(page, size, keyword, sort);
        log.info("공지사항 조회 성공 - 현재 페이지 데이터 개수: {}", response.getNotices().size());
        return ResponseEntity.ok(response);
    }

    // 공지사항 상단 5개 조회
    @GetMapping("/api/stu/notices/top5")
    public ResponseEntity<List<NoticeTop5Response>> getTop5Notices() {
        log.info("메인 화면용 상단 공지 5개 조회 요청");
        List<NoticeTop5Response> responses = noticeService.getTop5Notices();
        return ResponseEntity.ok(responses);
    }

    // 공지사항 단일글 조회
    @GetMapping("/api/stu/notices/{postId}")
    public ResponseEntity<NoticeDetailResponse> getNoticeDetail(@PathVariable Long postId) {
        log.info("공지사항 상세 조회 요청 - ID: {}", postId);
        // 조회수 증가와 상세 데이터 반환을 한 번에 처리
        NoticeDetailResponse response = noticeService.getNoticeDetail(postId);
        return ResponseEntity.ok(response);
    }

    // 공지사항 좋아요
    @PatchMapping("/api/stu/notices/{postId}/like")
    public ResponseEntity<NoticeResponse> toggleLike(@PathVariable Long postId) {
        log.info("공지사항 좋아요 토글 요청 - 게시글 ID: {}", postId);
        return ResponseEntity.ok(noticeService.toggleNoticeLike(postId));
    }

    // 공지사항 등록
    @PostMapping(value = "/api/admin/stu/notices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NoticeResponse> createNotice(@ModelAttribute NoticeRequest request) { // @RequestBody -> @ModelAttribute
        log.info("등록 요청 데이터: {}", request);
        return ResponseEntity.ok(noticeService.createNotice(request));
    }

    // 공지사항 수정
    @PutMapping("/api/admin/stu/notices/{postId}")
    public ResponseEntity<NoticeResponse> updateNotice(
            @PathVariable Long postId,
            @RequestBody NoticeUpdateRequest request) {
        return ResponseEntity.ok(noticeService.updateNotice(postId, request));
    }

    // 공지사항 삭제
    @DeleteMapping("/api/admin/stu/notices/{postId}")
    public ResponseEntity<NoticeResponse> deleteNotice(@PathVariable Long postId) {
        return ResponseEntity.ok(noticeService.deleteNotice(postId));
    }



}

