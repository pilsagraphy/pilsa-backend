package com.back.board.service;

import com.back.board.dto.*;
import com.back.board.exception.BoardException;
import com.back.board.mapper.BoardMapper;
import com.back.global.security.AuthUtils;
import com.back.global.util.FileStorageUtil;
import com.back.mypage.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 게시판 통합 서비스.
 *
 * 게시판별 정책(열람/작성 권한, 익명·비밀댓글·첨부·카테고리 사용 여부, 기본 카테고리)은
 * 전부 boards 테이블에서 읽는다({@link BoardPolicyService}). 관리자가 게시판을 새로 만들어도
 * 코드 수정 없이 동일하게 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardServiceImpl implements BoardService {

    private final BoardMapper boardMapper;
    private final BoardPolicyService boardPolicyService;
    private final FileStorageUtil fileStorageUtil;
    private final NotificationService notificationService;

    // 게시판 카테고리 목록 조회 (카테고리 미사용 게시판은 빈 목록)
    @Override
    public List<CategoryResponse> getCategoryList(Long boardId) {
        BoardPolicy policy = boardPolicyService.requireReadable(boardId);
        if (!policy.isCategoryUsed()) {
            return List.of();
        }
        return boardMapper.findCategoriesByBoardId(boardId);
    }

    // 메인용 상단 5개 조회
    @Override
    public List<BoardTop5Response> getTop5Posts(Long boardId) {
        boardPolicyService.requireReadable(boardId);
        return boardMapper.findTop5Posts(boardId);
    }

    // 게시판 전체 목록 조회
    @Override
    public BoardPageResponse getPostList(Long boardId, int page, int size, Long categoryId, String keyword, String sort) {
        boardPolicyService.requireReadable(boardId);

        int totalCount = boardMapper.countPosts(boardId, categoryId, keyword);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        // 글이 없는 게시판도 정상 상태다 (새로 만든 게시판은 항상 0건) → 빈 목록으로 응답
        List<BoardListResponse> posts = totalCount == 0
                ? List.of()
                : boardMapper.findAllPosts(boardId, categoryId, (page - 1) * size, size, keyword, sort);

        BoardPageResponse response = new BoardPageResponse();
        response.setTotalPages(totalPages);
        response.setTotalCount(totalCount);
        response.setPosts(posts);
        return response;
    }

    // 게시글 단일 상세 조회 (조회수 증가 및 첨부/좋아요/댓글 포함)
    @Override
    @Transactional
    public BoardDetailResponse getPostDetail(Long boardId, Long postId, String sort) {
        boardPolicyService.requireReadable(boardId);
        Long currentUserId = AuthUtils.currentUserId();

        BoardDetailResponse detail = boardMapper.findPostDetailById(postId, boardId, sort);
        if (detail == null) {
            throw new BoardException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }

        // 이전글/다음글 API 경로 가공
        String baseApi = "/api/boards/" + boardId + "/posts/";
        if (detail.getPrevPostApi() != null) detail.setPrevPostApi(baseApi + detail.getPrevPostApi());
        if (detail.getNextPostApi() != null) detail.setNextPostApi(baseApi + detail.getNextPostApi());

        boardMapper.updateViewCount(postId);

        List<AttachmentFileResponse> attachments = boardMapper.findAttachmentsByPostId(postId);
        detail.setAttachments(attachments);
        detail.setAttachmentCount(attachments != null ? attachments.size() : 0);
        detail.setLikeCount(boardMapper.countLikesByPostId(postId));
        detail.setComments(boardMapper.findCommentsByPostId(postId));
        detail.setIsLiked(boardMapper.existsLikeByPostIdAndUserId(postId, currentUserId));

        return detail;
    }

    // 게시글 좋아요 토글
    @Override
    @Transactional
    public BoardResponse togglePostLike(Long boardId, Long postId) {
        boardPolicyService.requireReadable(boardId);
        Long userId = AuthUtils.currentUserId();

        if (boardMapper.existsLikeByPostIdAndUserId(postId, userId)) {
            boardMapper.deleteLike(postId, userId);
            return new BoardResponse("좋아요 취소");
        }
        boardMapper.insertLike(postId, userId);
        return new BoardResponse("좋아요 +1");
    }

    // 게시글 신규 등록 (파일 업로드 포함)
    @Override
    @Transactional
    public BoardResponse createPost(Long boardId, BoardRequest request) {
        BoardPolicy policy = boardPolicyService.requireWritable(boardId);
        Long userId = AuthUtils.currentUserId();

        applyWritePolicy(policy, request);

        boardMapper.insertPost(request, userId, boardId);

        if (policy.isAttachmentAllowed() && request.getFiles() != null && !request.getFiles().isEmpty()) {
            for (MultipartFile file : request.getFiles()) {
                if (!file.isEmpty()) {
                    String savedPath = fileStorageUtil.save(file, policy.uploadDir(), null);
                    boardMapper.insertAttachment(
                            request.getPostId(),
                            file.getOriginalFilename(),
                            savedPath,
                            file.getSize(),
                            file.getContentType()
                    );
                }
            }
        }
        return new BoardResponse("게시글이 성공적으로 등록되었습니다.");
    }

    // 게시글 수정: 관리자이거나 작성자 본인일 경우 가능
    @Override
    @Transactional
    public BoardResponse updatePost(Long boardId, Long postId, BoardUpdateRequest request) {
        BoardPolicy policy = boardPolicyService.requireReadable(boardId);
        Long currentUserId = AuthUtils.currentUserId();
        Long authorId = boardMapper.findAuthorIdByPostId(postId);

        if (!AuthUtils.isAdmin() && !currentUserId.equals(authorId)) {
            throw new BoardException("수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        // 카테고리/익명/중요표시도 등록과 동일한 정책으로 보정
        if (request.getCategoryId() != null && !isValidCategory(policy, request.getCategoryId())) {
            request.setCategoryId(policy.getDefaultCategoryId());
        }
        if (!policy.isAnonymousAllowed()) {
            request.setIsAnonymous(false);
        }
        request.setIsPinned(resolvePinned(request.getIsPinned()));

        int updated = boardMapper.updatePost(postId, request);
        if (updated == 0) {
            // state != normal (블라인드/삭제) 이거나 존재하지 않는 글
            throw new BoardException("수정할 수 없는 게시글입니다.", HttpStatus.NOT_FOUND);
        }
        return new BoardResponse("게시글이 성공적으로 수정되었습니다.");
    }

    /**
     * 게시글 삭제 (소프트).
     * 관리자는 모든 글, 일반 회원은 본인 글만 삭제할 수 있다.
     */
    @Override
    @Transactional
    public BoardResponse deletePost(Long boardId, Long postId) {
        boardPolicyService.requireReadable(boardId);
        Long currentUserId = AuthUtils.currentUserId();

        if (!AuthUtils.isAdmin()) {
            Long authorId = boardMapper.findAuthorIdByPostId(postId);
            if (!currentUserId.equals(authorId)) {
                throw new BoardException("본인 글만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
            }
        }

        int deleted = boardMapper.deletePost(postId);
        if (deleted == 0) throw new BoardException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        return new BoardResponse("게시글이 성공적으로 삭제되었습니다.");
    }

    // 댓글/대댓글 신규 등록
    @Override
    @Transactional
    public CommentResponse createComment(Long boardId, Long postId, CommentRequest request) {
        BoardPolicy policy = boardPolicyService.requireReadable(boardId);
        Long userId = AuthUtils.currentUserId();

        if (!policy.isCommentAllowed()) {
            throw new BoardException("이 게시판은 댓글을 사용하지 않습니다.", HttpStatus.FORBIDDEN);
        }

        Long parentCommentId = request.getParentCommentId();
        if (parentCommentId != null && !boardMapper.existsCommentInPost(parentCommentId, postId)) {
            throw new BoardException("답글을 달 부모 댓글이 존재하지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        // 게시판이 허용하지 않는 옵션은 무시 (프론트가 잘못 보내도 정책이 이긴다)
        if (!policy.isAnonymousAllowed()) request.setIsAnonymous(false);
        if (!policy.isPrivateCommentAllowed()) request.setIsPrivate(false);

        boardMapper.insertComment(postId, userId, request);

        // 알림: 원글 작성자에게(본인 댓글 제외), 대댓글이면 부모 댓글 작성자에게
        notifyComment(boardId, postId, parentCommentId, userId);

        return new CommentResponse("댓글이 성공적으로 등록되었습니다.");
    }

    // 댓글 수정: 관리자이거나 작성자 본인일 경우 가능
    @Override
    @Transactional
    public CommentResponse updateComment(Long boardId, Long commentId, CommentRequest request) {
        BoardPolicy policy = boardPolicyService.requireReadable(boardId);
        Long currentUserId = AuthUtils.currentUserId();
        Long authorId = boardMapper.findCommentAuthorId(commentId);

        if (!AuthUtils.isAdmin() && !currentUserId.equals(authorId)) {
            throw new BoardException("댓글 수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        if (!policy.isAnonymousAllowed()) request.setIsAnonymous(false);
        if (!policy.isPrivateCommentAllowed()) request.setIsPrivate(false);

        boardMapper.updateComment(commentId, request);
        return new CommentResponse("댓글이 성공적으로 수정되었습니다.");
    }

    // 댓글 삭제 (소프트): 관리자 또는 작성자 본인
    @Override
    @Transactional
    public BoardResponse deleteComment(Long boardId, Long commentId) {
        boardPolicyService.requireReadable(boardId);
        Long currentUserId = AuthUtils.currentUserId();
        Long authorId = boardMapper.findCommentAuthorId(commentId);

        if (!AuthUtils.isAdmin() && !currentUserId.equals(authorId)) {
            throw new BoardException("본인 댓글만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        boardMapper.deleteComment(commentId);
        return new BoardResponse("댓글이 성공적으로 삭제되었습니다.");
    }

    // ---- 내부 헬퍼 ----

    // 등록 요청에 게시판 정책을 적용 (카테고리 보정, 익명 허용 여부, 중요표시 권한)
    private void applyWritePolicy(BoardPolicy policy, BoardRequest request) {
        if (!policy.isCategoryUsed()) {
            request.setCategoryId(null);
        } else if (!isValidCategory(policy, request.getCategoryId())) {
            // 미선택이거나 이 게시판에 없는 값이면 게시판 기본값으로 대체
            request.setCategoryId(policy.getDefaultCategoryId());
        }
        if (!policy.isAnonymousAllowed()) {
            request.setIsAnonymous(false);
        }
        request.setIsPinned(resolvePinned(request.getIsPinned()));
    }

    private boolean isValidCategory(BoardPolicy policy, Long categoryId) {
        return categoryId != null && boardMapper.existsCategory(categoryId, policy.getBoardId());
    }

    // 중요표시(상단 고정)는 게시판 종류와 무관하게 관리자(레벨 1~3)만 설정할 수 있다
    private Boolean resolvePinned(Boolean requested) {
        if (Boolean.TRUE.equals(requested) && !AuthUtils.isAdmin()) {
            log.debug("일반 회원의 isPinned 요청 무시 - userId: {}", AuthUtils.currentUserIdOrNull());
            return false;
        }
        return Boolean.TRUE.equals(requested);
    }

    // 댓글/대댓글 알림 발행 (실패해도 댓글 등록 자체는 성공시킨다)
    private void notifyComment(Long boardId, Long postId, Long parentCommentId, Long actorId) {
        try {
            Long postAuthorId = boardMapper.findAuthorIdByPostId(postId);
            String link = "/api/boards/" + boardId + "/posts/" + postId;

            if (parentCommentId != null) {
                Long parentAuthorId = boardMapper.findCommentAuthorId(parentCommentId);
                notificationService.notifyReply(parentAuthorId, actorId, postId, link);
                // 원글 작성자가 부모 댓글 작성자와 다르면 원글 작성자에게도 알림
                if (postAuthorId != null && !postAuthorId.equals(parentAuthorId)) {
                    notificationService.notifyComment(postAuthorId, actorId, postId, link);
                }
            } else {
                notificationService.notifyComment(postAuthorId, actorId, postId, link);
            }
        } catch (Exception e) {
            log.warn("댓글 알림 발행 실패 - postId: {}, {}", postId, e.getMessage());
        }
    }
}
