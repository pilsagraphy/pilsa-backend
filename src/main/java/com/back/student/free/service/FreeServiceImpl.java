package com.back.student.free.service;

import com.back.student.common.FileStorageUtil;
import com.back.student.free.dto.*; // Free 패키지의 DTO들 임포트
import com.back.student.free.exception.FreeException;
import com.back.student.free.mapper.FreeMapper;
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
public class FreeServiceImpl implements FreeService {

    private final FreeMapper freeMapper;
    private final FileStorageUtil fileStorageUtil;
    private final Long FREE_BOARD_ID = 2L; // 자유게시판 고유 ID 2번 고정

    // 현재 로그인한 사용자의 고유 ID(PK) 추출 (토큰이 없거나 유효하지 않으면 여기서 차단)
    private Long getCurrentUserId() {
        String subValue = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(subValue);
        } catch (NumberFormatException e) {
            // 외부인이 접근을 시도할 경우 여기서 즉시 차단됨
            throw new FreeException("로그인이 필요한 서비스입니다.", HttpStatus.UNAUTHORIZED);
        }
    }

    // 현재 사용자가 관리자(ROLE_ADMIN) 권한을 가졌는지 확인
    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // 자유게시판 내 카테고리 목록 조회 (조회 시 인증 체크)
    @Override
    public List<CategoryResponse> getCategoryList() {
        getCurrentUserId();
        return freeMapper.findCategoriesByBoardId(FREE_BOARD_ID);
    }

    // 메인용 최신글 5개 조회 (조회 시 인증 체크)
    @Override
    public List<FreeTop5Response> getTop5Posts() {
        getCurrentUserId();
        return freeMapper.findTop5Posts(FREE_BOARD_ID);
    }

    // 자유게시판 전체 목록 조회 (메서드 시작 시 인증을 체크하여 외부인의 조회를 원천 차단함)
    @Override
    public FreePageResponse getPostList(int page, int size, Long categoryId, String keyword, String sort) {
        // 1. 로그인 여부 확인
        getCurrentUserId();

        // 2. 전체 게시글 개수 조회
        int totalCount = freeMapper.countPosts(FREE_BOARD_ID, categoryId, keyword);
        if (totalCount == 0) {
            throw new FreeException("등록된 게시글이 없습니다.", HttpStatus.NOT_FOUND);
        }

        // 3. 페이지 유효성 검사 및 목록 호출
        int totalPages = (int) Math.ceil((double) totalCount / size);
        if (page > totalPages) {
            throw new FreeException("존재하지 않는 페이지입니다.", HttpStatus.BAD_REQUEST);
        }

        int offset = (page - 1) * size;
        List<FreeListResponse> posts = freeMapper.findAllPosts(FREE_BOARD_ID, categoryId, offset, size, keyword, sort);

        FreePageResponse response = new FreePageResponse();
        response.setTotalPages(totalPages);
        response.setPosts(posts);
        return response;
    }

    // 자유게시글 단일 상세 조회 (인증된 사용자만 가능하며 조회수 증가 및 댓글 포함)
    @Override
    @Transactional
    public FreeDetailResponse getPostDetail(Long postId, String sort) {
        // 1. 로그인 여부 확인 및 상세 데이터 조회
        Long currentUserId = getCurrentUserId();
        FreeDetailResponse detail = freeMapper.findPostDetailById(postId, FREE_BOARD_ID, sort);
        if (detail == null) {
            throw new FreeException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }

        // 2. API 경로 가공
        String baseApi = "/api/stu/free/posts/";
        if (detail.getPrevPostApi() != null) detail.setPrevPostApi(baseApi + detail.getPrevPostApi());
        if (detail.getNextPostApi() != null) detail.setNextPostApi(baseApi + detail.getNextPostApi());

        // 3. 조회수 증가
        freeMapper.updateViewCount(postId);

        // 4. 첨부파일, 좋아요 수, 댓글 리스트 로드
        List<AttachmentFileResponse> attachments = freeMapper.findAttachmentsByPostId(postId);
        detail.setAttachments(attachments);
        detail.setAttachmentCount(attachments != null ? attachments.size() : 0);
        detail.setLikeCount(freeMapper.countLikesByPostId(postId));
        detail.setComments(freeMapper.findCommentsByPostId(postId));

        // 5. 좋아요 클릭 여부 체크
        try {
            detail.setLiked(freeMapper.existsLikeByPostIdAndUserId(postId, currentUserId));
        } catch (Exception e) {
            detail.setLiked(false);
        }

        return detail;
    }

    // 게시글 좋아요 토글 기능
    @Override
    @Transactional
    public FreeResponse togglePostLike(Long postId) {
        Long userId = getCurrentUserId();
        boolean isLiked = freeMapper.existsLikeByPostIdAndUserId(postId, userId);

        if (isLiked) {
            freeMapper.deleteLike(postId, userId);
            return new FreeResponse("좋아요 취소");
        } else {
            freeMapper.insertLike(postId, userId);
            return new FreeResponse("좋아요 +1");
        }
    }

    // 자유게시글 신규 등록 (파일 업로드 처리 포함)
    @Override
    @Transactional
    public FreeResponse createPost(FreeRequest request) {
        Long userId = getCurrentUserId();

        // 카테고리 미선택 시 기본값 1번으로 넣도록 함
        if (request.getCategoryId() == null) {
            request.setCategoryId(1L);
        }

        // 게시글 본문 저장
        freeMapper.insertPost(request, userId, FREE_BOARD_ID);

        // 파일 업로드 처리
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            for (MultipartFile file : request.getFiles()) {
                if (!file.isEmpty()) {
                    String savedPath = fileStorageUtil.save(file, "uploads/free", null);
                    // 공지사항과 동일한 필드명 파라미터 전달
                    freeMapper.insertAttachment(
                            request.getPostId(),
                            file.getOriginalFilename(),
                            savedPath,
                            file.getSize(),
                            file.getContentType()
                    );
                }
            }
        }
        return new FreeResponse("자게가 성공적으로 등록되었습니다.");
    }

    // 게시글 수정: 관리자 권한이 있거나 작성자 본인일 경우 가능
    @Override
    @Transactional
    public FreeResponse updatePost(Long postId, FreeUpdateRequest request) {
        Long currentUserId = getCurrentUserId();
        Long authorId = freeMapper.findAuthorIdByPostId(postId);

        if (!isAdmin() && !currentUserId.equals(authorId)) {
            throw new FreeException("수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        int updated = freeMapper.updatePost(postId, request);
        if (updated == 0) throw new FreeException("게시글 수정에 실패했습니다.", HttpStatus.NOT_FOUND);
        return new FreeResponse("자게가 성공적으로 수정되었습니다.");
    }

    // 게시글 삭제: 오직 작성자 본인만 가능 (관리자 권한 제외)
    @Override
    @Transactional
    public FreeResponse deletePost(Long postId) {
        Long currentUserId = getCurrentUserId();
        Long authorId = freeMapper.findAuthorIdByPostId(postId);

        if (!currentUserId.equals(authorId)) {
            throw new FreeException("본인 글만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        freeMapper.deletePost(postId);
        return new FreeResponse("자게가 성공적으로 삭제되었습니다.");
    }

    // 게시글에 대한 댓글 신규 등록
    @Override
    @Transactional
    public CommentResponse createComment(Long postId, CommentRequest request) {
        Long userId = getCurrentUserId();
        freeMapper.insertComment(postId, userId, request);
        return new CommentResponse("댓글이 성공적으로 등록되었습니다.", LocalDateTime.now());
    }

    // 댓글 수정: 관리자 권한이 있거나 작성자 본인일 경우 가능
    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId, CommentRequest request) {
        Long currentUserId = getCurrentUserId();
        Long authorId = freeMapper.findCommentAuthorId(commentId);

        if (!isAdmin() && !currentUserId.equals(authorId)) {
            throw new FreeException("댓글 수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        freeMapper.updateComment(commentId, request);
        return new CommentResponse("댓글이 성공적으로 수정되었습니다.", LocalDateTime.now());
    }

    // 댓글 삭제: 오직 댓글 작성자 본인만 가능 (관리자 권한 제외)
    @Override
    @Transactional
    public FreeResponse deleteComment(Long commentId) {
        Long currentUserId = getCurrentUserId();
        Long authorId = freeMapper.findCommentAuthorId(commentId);

        if (!currentUserId.equals(authorId)) {
            throw new FreeException("본인 댓글만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        freeMapper.deleteComment(commentId);
        return new FreeResponse("댓글이 성공적으로 삭제되었습니다.");
    }
}