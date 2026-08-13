package com.back.board.service;

import com.back.board.dto.*;
import com.back.board.exception.BoardException;
import com.back.board.mapper.BoardMapper;
import com.back.board.type.BoardType;
import com.back.global.util.FileStorageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 게시판(공지/자유/정보) 통합 서비스 구현.
 * boardId → {@link BoardType} 로 게시판별 정책(권한/카테고리 기본값/업로드 경로)을 결정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardServiceImpl implements BoardService {

    private final BoardMapper boardMapper;
    private final FileStorageUtil fileStorageUtil;

    // 현재 로그인한 사용자의 고유 ID(PK) 추출 (토큰이 없거나 유효하지 않으면 여기서 차단)
    private Long getCurrentUserId() {
        String subValue = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(subValue);
        } catch (NumberFormatException e) {
            throw new BoardException("로그인이 필요한 서비스입니다.", HttpStatus.UNAUTHORIZED);
        }
    }

    // 현재 사용자가 관리자(ROLE_ADMIN) 권한을 가졌는지 확인
    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // 게시판 카테고리 목록 조회 (공지사항은 빈 목록)
    @Override
    public List<CategoryResponse> getCategoryList(Long boardId) {
        BoardType.of(boardId);
        getCurrentUserId();
        return boardMapper.findCategoriesByBoardId(boardId);
    }

    // 메인용 최신글 5개 조회
    @Override
    public List<BoardTop5Response> getTop5Posts(Long boardId) {
        BoardType.of(boardId);
        getCurrentUserId();
        return boardMapper.findTop5Posts(boardId);
    }

    // 게시판 전체 목록 조회 (인증 체크로 외부인 조회 차단)
    @Override
    public BoardPageResponse getPostList(Long boardId, int page, int size, Long categoryId, String keyword, String sort) {
        BoardType.of(boardId);
        getCurrentUserId();

        int totalCount = boardMapper.countPosts(boardId, categoryId, keyword);
        if (totalCount == 0) {
            throw new BoardException("등록된 게시글이 없습니다.", HttpStatus.NOT_FOUND);
        }

        int totalPages = (int) Math.ceil((double) totalCount / size);
        if (page > totalPages) {
            throw new BoardException("존재하지 않는 페이지입니다.", HttpStatus.BAD_REQUEST);
        }

        int offset = (page - 1) * size;
        List<BoardListResponse> posts = boardMapper.findAllPosts(boardId, categoryId, offset, size, keyword, sort);

        BoardPageResponse response = new BoardPageResponse();
        response.setTotalPages(totalPages);
        response.setPosts(posts);
        return response;
    }

    // 게시글 단일 상세 조회 (조회수 증가 및 첨부/좋아요/댓글 포함)
    @Override
    @Transactional
    public BoardDetailResponse getPostDetail(Long boardId, Long postId, String sort) {
        BoardType.of(boardId);
        Long currentUserId = getCurrentUserId();

        BoardDetailResponse detail = boardMapper.findPostDetailById(postId, boardId, sort);
        if (detail == null) {
            throw new BoardException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }

        // 이전글/다음글 API 경로 가공
        String baseApi = "/api/stu/" + boardId + "/posts/";
        if (detail.getPrevPostApi() != null) detail.setPrevPostApi(baseApi + detail.getPrevPostApi());
        if (detail.getNextPostApi() != null) detail.setNextPostApi(baseApi + detail.getNextPostApi());

        // 조회수 증가
        boardMapper.updateViewCount(postId);

        // 첨부파일, 좋아요 수, 댓글 리스트 로드
        List<AttachmentFileResponse> attachments = boardMapper.findAttachmentsByPostId(postId);
        detail.setAttachments(attachments);
        detail.setAttachmentCount(attachments != null ? attachments.size() : 0);
        detail.setLikeCount(boardMapper.countLikesByPostId(postId));
        detail.setComments(boardMapper.findCommentsByPostId(postId));

        // 좋아요 클릭 여부 체크
        try {
            detail.setLiked(boardMapper.existsLikeByPostIdAndUserId(postId, currentUserId));
        } catch (Exception e) {
            detail.setLiked(false);
        }

        return detail;
    }

    // 게시글 좋아요 토글
    @Override
    @Transactional
    public BoardResponse togglePostLike(Long boardId, Long postId) {
        BoardType.of(boardId);
        Long userId = getCurrentUserId();
        boolean isLiked = boardMapper.existsLikeByPostIdAndUserId(postId, userId);

        if (isLiked) {
            boardMapper.deleteLike(postId, userId);
            return new BoardResponse("좋아요 취소");
        } else {
            boardMapper.insertLike(postId, userId);
            return new BoardResponse("좋아요 +1");
        }
    }

    // 게시글 신규 등록 (파일 업로드 포함). 공지사항은 관리자만 등록 가능.
    @Override
    @Transactional
    public BoardResponse createPost(Long boardId, BoardRequest request) {
        BoardType boardType = BoardType.of(boardId);
        Long userId = getCurrentUserId();

        // 공지사항 등 관리자 전용 게시판은 권한 확인 (SecurityConfig에서도 막히지만 이중 방어)
        if (boardType.isAdminWrite() && !isAdmin()) {
            throw new BoardException("해당 게시판에 글을 등록할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        // 카테고리 정리: 미선택이거나 이 게시판에 없는 값이면 게시판별 기본값으로 대체.
        // (Swagger가 빈 값에 채워 넣는 임의의 큰 숫자 등 잘못된 값이 들어와도 등록이 실패하지 않도록 방어)
        Long categoryId = request.getCategoryId();
        boolean invalidCategory = categoryId == null || !boardMapper.existsCategory(categoryId, boardId);
        if (invalidCategory) {
            request.setCategoryId(boardType.getDefaultCategoryId()); // 카테고리 미사용 게시판(공지)은 null
        }

        // 게시글 본문 저장
        boardMapper.insertPost(request, userId, boardId);

        // 파일 업로드 처리
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            for (MultipartFile file : request.getFiles()) {
                if (!file.isEmpty()) {
                    String savedPath = fileStorageUtil.save(file, boardType.getUploadDir(), null);
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
        BoardType.of(boardId);
        Long currentUserId = getCurrentUserId();
        Long authorId = boardMapper.findAuthorIdByPostId(postId);

        if (!isAdmin() && !currentUserId.equals(authorId)) {
            throw new BoardException("수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        int updated = boardMapper.updatePost(postId, request);
        if (updated == 0) throw new BoardException("게시글 수정에 실패했습니다.", HttpStatus.NOT_FOUND);
        return new BoardResponse("게시글이 성공적으로 수정되었습니다.");
    }

    /**
     * 게시글 삭제.
     *  - 공지사항(관리자 전용 게시판) : 관리자만 삭제 가능
     *  - 자유/정보게시판             : 작성자 본인만 삭제 가능
     */
    @Override
    @Transactional
    public BoardResponse deletePost(Long boardId, Long postId) {
        BoardType boardType = BoardType.of(boardId);
        Long currentUserId = getCurrentUserId();

        if (boardType.isAdminWrite()) {
            if (!isAdmin()) {
                throw new BoardException("삭제 권한이 없습니다.", HttpStatus.FORBIDDEN);
            }
        } else {
            Long authorId = boardMapper.findAuthorIdByPostId(postId);
            if (!currentUserId.equals(authorId)) {
                throw new BoardException("본인 글만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
            }
        }

        int deleted = boardMapper.deletePost(postId);
        if (deleted == 0) throw new BoardException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        return new BoardResponse("게시글이 성공적으로 삭제되었습니다.");
    }

    // 댓글/대댓글 신규 등록 (parentCommentId 있으면 답글, 자유=익명, 정보=비밀댓글)
    @Override
    @Transactional
    public CommentResponse createComment(Long boardId, Long postId, CommentRequest request) {
        BoardType.of(boardId);
        Long userId = getCurrentUserId();

        // 대댓글이면 부모 댓글이 같은 게시글에 실제로 있는지 확인 (엉뚱한 부모 지정 방지)
        Long parentCommentId = request.getParentCommentId();
        if (parentCommentId != null && !boardMapper.existsCommentInPost(parentCommentId, postId)) {
            throw new BoardException("답글을 달 부모 댓글이 존재하지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        boardMapper.insertComment(postId, userId, request);
        return new CommentResponse("댓글이 성공적으로 등록되었습니다.");
    }

    // 댓글 수정: 관리자이거나 작성자 본인일 경우 가능
    @Override
    @Transactional
    public CommentResponse updateComment(Long boardId, Long commentId, CommentRequest request) {
        BoardType.of(boardId);
        Long currentUserId = getCurrentUserId();
        Long authorId = boardMapper.findCommentAuthorId(commentId);

        if (!isAdmin() && !currentUserId.equals(authorId)) {
            throw new BoardException("댓글 수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        boardMapper.updateComment(commentId, request);
        return new CommentResponse("댓글이 성공적으로 수정되었습니다.");
    }

    // 댓글 삭제: 오직 댓글 작성자 본인만 가능
    @Override
    @Transactional
    public BoardResponse deleteComment(Long boardId, Long commentId) {
        BoardType.of(boardId);
        Long currentUserId = getCurrentUserId();
        Long authorId = boardMapper.findCommentAuthorId(commentId);

        if (!currentUserId.equals(authorId)) {
            throw new BoardException("본인 댓글만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        boardMapper.deleteComment(commentId);
        return new BoardResponse("댓글이 성공적으로 삭제되었습니다.");
    }
}
