package com.back.student.notice.service;

import com.back.student.common.FileStorageUtil;
import com.back.student.notice.dto.*;
import com.back.student.notice.exception.NoticeException;
import com.back.student.notice.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class NoticeServiceImpl implements NoticeService {

    private final NoticeMapper noticeMapper;
    private final FileStorageUtil fileStorageUtil;
    private final Long NOTICE_BOARD_ID = 1L; // 공지사항 게시판 고유 ID 1번으로 고정

    // 현재 로그인한 사용자의 고유 ID(PK) 가져오기
    // 서버 부담 덜기 위해 db조회 안하고 토큰으로
    private Long getCurrentUserId() {
        // sub 값으로 들어온거를 가져옴
        String subValue = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(subValue);
        } catch (NumberFormatException e) {
            // 혹시나 숫자가 아닌 값이 들어올 경우를 대비한 예외 처리
            throw new NoticeException("유효하지 않은 사용자 ID 형식입니다: " + subValue, HttpStatus.UNAUTHORIZED);
        }
    }
    // 현재 사용자가 관리자(ROLE_ADMIN)인지 확인
    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // 공지사항 전체 조회
    @Override
    public NoticePageResponse getNoticeList(int page, int size, String keyword, String sort) {
        // 1. 전체 게시글 개수 먼저 확인 (데이터가 아예 없는지 체크)
        int totalCount = noticeMapper.countNotices(NOTICE_BOARD_ID, keyword);
        // [예외 처리] 검색 결과나 게시글이 하나도 없는 경우 서비스에서 즉시 예외 발생
        if (totalCount == 0) {
            throw new NoticeException("등록된 공지사항이 없습니다.", HttpStatus.NOT_FOUND);
        }
        // 2. 전체 페이지 수 계산
        int totalPages = (int) Math.ceil((double) totalCount / size);

        // [예외 처리] 요청한 페이지 번호가 전체 페이지 수보다 클 경우
        if (page > totalPages) {
            throw new NoticeException("존재하지 않는 페이지입니다. 현재 마지막 페이지는 " + totalPages + "입니다.", HttpStatus.BAD_REQUEST);
        }
        // 3. DB 조회를 위한 시작 지점(offset) 계산
        int offset = (page - 1) * size;
        // 4. 해당 페이지에 해당하는 글 목록 가져오기 - 사이즈 만큼, 정렬기준에 맞춤
        List<NoticeListResponse> notices = noticeMapper.findAllNotices(NOTICE_BOARD_ID, offset, size, keyword, sort);
        // 5. 응답 객체 생성 및 반환
        NoticePageResponse response = new NoticePageResponse();
        response.setTotalPages(totalPages);
        response.setNotices(notices);

        return response;
    }

    // 상단 5개 조회
    @Override
    public List<NoticeTop5Response> getTop5Notices() {
        // 정렬 로직이 포함된 매퍼 메서드 호출
        return noticeMapper.findTop5Notices(NOTICE_BOARD_ID);
    }

    // 공지사항 단일글 조회
    @Override
    @Transactional // 조회수 업데이트가 포함되므로 readOnly 제거 또는 별도 처리
    public NoticeDetailResponse getNoticeDetail(Long postId, String sort) { // 정렬기준과 현재 기준값을 받음
        // 1. 상세 데이터 조회
        NoticeDetailResponse detail = noticeMapper.findNoticeDetailById(postId, NOTICE_BOARD_ID, sort);
        //[예외처리] 존재하지 않는 게시글 예외 처리
        if (detail == null) {
            throw new NoticeException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }
        // 2. ID 숫자들을 API 경로 문자열로 변환
        String baseApi = "/api/stu/notices/";
        // 이전글 URL 생성
        if (detail.getPrevPostApi() != null) {
            detail.setPrevPostApi(baseApi + detail.getPrevPostApi());
        }
        // 다음글 URL 생성
        if (detail.getNextPostApi() != null) {
            detail.setNextPostApi(baseApi + detail.getNextPostApi());
        }
        // 3. 조회수 1 증가
        noticeMapper.updateViewCount(postId);
        // 4. 첨부파일 ID 리스트 조회 (필요 시 매퍼에서 별도 호출)
        List<AttachmentFileResponse> attachments = noticeMapper.findAttachmentIdsByPostId(postId);
        detail.setAttachments(attachments);
        detail.setAttachmentCount(attachments != null ? attachments.size() : 0); // 첨부파일 개수 세팅 (리스트의 size 활용)
        // 5. 좋아요 개수 추가
        detail.setLikeCount(noticeMapper.countLikesByPostId(postId));
        // 6. 로그인한 사용자의 좋아요 여부 체크
        try {
            // 토큰이 없거나 잘못되면 예외 발생
            Long userId = getCurrentUserId();
            detail.setLiked(noticeMapper.existsLikeByPostIdAndUserId(postId, userId));
        } catch (Exception e) {
            // 비로그인 상태이거나 토큰 오류 시 false 처리
            detail.setLiked(false);
        }

        return detail;
    }

    // 좋아요
    @Override
    @Transactional
    public NoticeResponse toggleNoticeLike(Long postId) {
        // 내부에서 현재 로그인한 유저 ID를 가져옴
        Long userId = getCurrentUserId();
        // 1. 해당 게시글에 내가 이미 좋아요를 눌렀는지 확인
        boolean isLiked = noticeMapper.existsLikeByPostIdAndUserId(postId, userId);
        String message;
        if (isLiked) {
            // 3. 이미 있다면? 좋아요 취소 (삭제)
            noticeMapper.deleteLike(postId, userId);
            message = "좋아요 취소";
        } else {
            // 4. 없다면? 좋아요 추가 (삽입)
            noticeMapper.insertLike(postId, userId);
            message = "좋아요 +1";
        }
        // 5. 서비스에서 직접 DTO에 담아서 반환
        return new NoticeResponse(message);
    }

    // 공지사항 등록
    @Override
    @Transactional
    public NoticeResponse createNotice(NoticeRequest request) {
        if (!isAdmin()) {
            throw new NoticeException("공지사항 등록 권한이 없습니다.", HttpStatus.FORBIDDEN);
            // 근데 사실 이거 SecurityConfig에서 막혀서 안뜨긴 합니다
        }
        Long userId = getCurrentUserId();
        noticeMapper.insertNotice(request, userId, NOTICE_BOARD_ID);
        Long generatedPostId = request.getPostId();
        // 첨부파일 연결
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            for (MultipartFile file : request.getFiles()) {
                if (!file.isEmpty()) {
                    // (1) FileStorageUtil로 서버 하드디스크에 저장
                    String savedPath = fileStorageUtil.save(file, "uploads/notices", null);
                    // (2) 위에서 얻은 경로를 DB attachments 테이블에 저장
                    noticeMapper.insertAttachment(
                            generatedPostId,
                            file.getOriginalFilename(),
                            savedPath,
                            file.getSize(),
                            file.getContentType()
                    );
                }
            }
        }
        return new NoticeResponse("공지사항이 성공적으로 등록되었습니다.");
    }

    // 공지사항 수정
    @Override
    @Transactional
    public NoticeResponse updateNotice(Long postId, NoticeUpdateRequest request) {
        if (!isAdmin()) {
            throw new NoticeException("공지사항 수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        int updated = noticeMapper.updateNotice(postId, request);
        if (updated == 0) {
            throw new NoticeException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }
        return new NoticeResponse("공지사항이 성공적으로 수정되었습니다.");
    }

    // 공지사항 삭제
    @Override
    @Transactional
    public NoticeResponse deleteNotice(Long postId) {
        if (!isAdmin()) {
            throw new NoticeException("공지사항 삭제 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        int deleted = noticeMapper.deleteNotice(postId);
        if (deleted == 0) {
            throw new NoticeException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }
        return new NoticeResponse("공지사항이 성공적으로 삭제되었습니다.");
    }
}