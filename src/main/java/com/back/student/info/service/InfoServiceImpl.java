package com.back.student.info.service;

import com.back.student.common.FileStorageUtil;
import com.back.student.info.dto.*;
import com.back.student.info.exception.InfoException;
import com.back.student.info.mapper.InfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InfoServiceImpl implements InfoService {

    private final InfoMapper infoMapper;
    private final FileStorageUtil fileStorageUtil;
    private final Long INFO_BOARD_ID = 3L; // 정보게시판 고유 ID

    // 현재 로그인한 사용자의 고유 ID(PK) 추출
    private Long getCurrentUserId() {
        String subValue = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(subValue);
        } catch (NumberFormatException e) {
            throw new InfoException("로그인이 필요한 서비스입니다.", HttpStatus.UNAUTHORIZED);
        }
    }

    // 현재 사용자가 관리자(ROLE_ADMIN) 권한을 가졌는지 확인
    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // 정보게시판 내 카테고리 목록 조회
    @Override
    public List<CategoryResponse> getCategoryList() {
        getCurrentUserId();
        return infoMapper.findCategoriesByBoardId(INFO_BOARD_ID);
    }

    // 메인용 최신글 5개 조회
    @Override
    public List<InfoTop5Response> getTop5Posts() {
        getCurrentUserId();
        return infoMapper.findTop5Posts(INFO_BOARD_ID);
    }

    // 정보게시판 전체 목록 조회
    @Override
    public InfoPageResponse getPostList(int page, int size, Long categoryId, String keyword, String sort) {
        getCurrentUserId();

        // 1. 전체 게시글 개수 조회
        int totalCount = infoMapper.countPosts(INFO_BOARD_ID, categoryId, keyword);
        if (totalCount == 0) {
            throw new InfoException("등록된 게시글이 없습니다.", HttpStatus.NOT_FOUND);
        }

        // 2. 페이지 유효성 검사
        int totalPages = (int) Math.ceil((double) totalCount / size);
        if (page > totalPages) {
            throw new InfoException("존재하지 않는 페이지입니다.", HttpStatus.BAD_REQUEST);
        }

        int offset = (page - 1) * size;
        List<InfoListResponse> posts = infoMapper.findAllPosts(INFO_BOARD_ID, categoryId, offset, size, keyword, sort);

        InfoPageResponse response = new InfoPageResponse();
        response.setTotalPages(totalPages);
        response.setPosts(posts);
        return response;
    }

    // 정보게시글 단일 상세 조회
    @Override
    @Transactional
    public InfoDetailResponse getPostDetail(Long postId, String sort) {
        Long currentUserId = getCurrentUserId();
        InfoDetailResponse detail = infoMapper.findPostDetailById(postId, INFO_BOARD_ID, sort);
        if (detail == null) {
            throw new InfoException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }

        // 1. API 경로 가공
        String baseApi = "/api/stu/info/posts/";
        if (detail.getPrevPostApi() != null) detail.setPrevPostApi(baseApi + detail.getPrevPostApi());
        if (detail.getNextPostApi() != null) detail.setNextPostApi(baseApi + detail.getNextPostApi());

        // 2. 조회수 증가
        infoMapper.updateViewCount(postId);

        // 3. 첨부파일, 좋아요 수, 댓글 리스트 로드
        List<AttachmentFileResponse> attachments = infoMapper.findAttachmentsByPostId(postId);
        detail.setAttachments(attachments);
        detail.setAttachmentCount(attachments != null ? attachments.size() : 0);
        detail.setLikeCount(infoMapper.countLikesByPostId(postId));
        detail.setComments(infoMapper.findCommentsByPostId(postId));

        // 4. 좋아요 클릭 여부 체크
        try {
            detail.setLiked(infoMapper.existsLikeByPostIdAndUserId(postId, currentUserId));
        } catch (Exception e) {
            detail.setLiked(false);
        }

        return detail;
    }

    // 게시글 좋아요 토글 기능
    @Override
    @Transactional
    public InfoResponse togglePostLike(Long postId) {
        Long userId = getCurrentUserId();
        boolean isLiked = infoMapper.existsLikeByPostIdAndUserId(postId, userId);

        if (isLiked) {
            infoMapper.deleteLike(postId, userId);
            return new InfoResponse("좋아요 취소");
        } else {
            infoMapper.insertLike(postId, userId);
            return new InfoResponse("좋아요 +1");
        }
    }

    // 정보게시글 신규 등록 (모두 실명 처리)
    @Override
    @Transactional
    public InfoResponse createPost(InfoRequest request) {
        Long userId = getCurrentUserId();

        // 카테고리 미선택 시 기본값 2번(전체/공통 등) 설정 < 자유게시판하고 디폴트 값 구별 위해 2번 사용
        if (request.getCategoryId() == null) {
            request.setCategoryId(2L);
        }

        // 게시글 본문 저장 (익명 필드 제외)
        infoMapper.insertPost(request, userId, INFO_BOARD_ID);

        // 파일 업로드 처리
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            for (MultipartFile file : request.getFiles()) {
                if (!file.isEmpty()) {
                    String savedPath = fileStorageUtil.save(file, "uploads/info", null);
                    infoMapper.insertAttachment(
                            request.getPostId(),
                            file.getOriginalFilename(),
                            savedPath,
                            file.getSize(),
                            file.getContentType()
                    );
                }
            }
        }
        return new InfoResponse("정보게시글이 성공적으로 등록되었습니다.");
    }

    // 게시글 수정: 관리자 혹은 작성자 본인
    @Override
    @Transactional
    public InfoResponse updatePost(Long postId, InfoUpdateRequest request) {
        Long currentUserId = getCurrentUserId();
        Long authorId = infoMapper.findAuthorIdByPostId(postId);

        if (!isAdmin() && !currentUserId.equals(authorId)) {
            throw new InfoException("수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        int updated = infoMapper.updatePost(postId, request);
        if (updated == 0) throw new InfoException("게시글 수정에 실패했습니다.", HttpStatus.NOT_FOUND);
        return new InfoResponse("정보게시글이 성공적으로 수정되었습니다.");
    }

    // 게시글 삭제: 오직 작성자 본인만 가능
    @Override
    @Transactional
    public InfoResponse deletePost(Long postId) {
        Long currentUserId = getCurrentUserId();
        Long authorId = infoMapper.findAuthorIdByPostId(postId);

        if (!currentUserId.equals(authorId)) {
            throw new InfoException("본인 글만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        infoMapper.deletePost(postId);
        return new InfoResponse("정보게시글이 성공적으로 삭제되었습니다.");
    }

    // 정보게시판 댓글 신규 등록 (비밀댓글 기능 포함)
    @Override
    @Transactional
    public CommentResponse createComment(Long postId, CommentRequest request) {
        Long userId = getCurrentUserId();
        // CommentRequest의 isPrivate 필드가 Mapper를 통해 DB에 저장됨
        infoMapper.insertComment(postId, userId, request);
        return new CommentResponse("댓글이 성공적으로 등록되었습니다.");
    }

    // 댓글 수정: 관리자 혹은 작성자 본인
    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId, CommentRequest request) {
        Long currentUserId = getCurrentUserId();
        Long authorId = infoMapper.findCommentAuthorId(commentId);

        if (!isAdmin() && !currentUserId.equals(authorId)) {
            throw new InfoException("댓글 수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        infoMapper.updateComment(commentId, request);
        return new CommentResponse("댓글이 성공적으로 수정되었습니다.");
    }

    // 댓글 삭제: 오직 댓글 작성자 본인만 가능
    @Override
    @Transactional
    public InfoResponse deleteComment(Long commentId) {
        Long currentUserId = getCurrentUserId();
        Long authorId = infoMapper.findCommentAuthorId(commentId);

        if (!currentUserId.equals(authorId)) {
            throw new InfoException("본인 댓글만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        infoMapper.deleteComment(commentId);
        return new InfoResponse("댓글이 성공적으로 삭제되었습니다.");
    }
}