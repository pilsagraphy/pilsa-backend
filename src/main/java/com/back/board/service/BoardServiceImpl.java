package com.back.board.service;

import com.back.board.attachment.service.AttachmentService;
import com.back.board.draft.mapper.DraftMapper;
import com.back.board.dto.*;
import com.back.board.exception.BoardException;
import com.back.board.mapper.BoardMapper;
import com.back.global.security.AuthUtils;
import com.back.global.util.FileStorageUtil;
import com.back.global.util.PageUtils;
import com.back.mypage.notification.dto.NotificationType;
import com.back.mypage.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // 상단 N개 조회 상한 — 프론트가 큰 값을 보내 목록 API를 대체해버리는 것을 막는다
    private static final int MAX_TOP_POSTS = 50;

    private final BoardMapper boardMapper;
    private final BoardPolicyService boardPolicyService;
    private final FileStorageUtil fileStorageUtil;
    private final AttachmentService attachmentService;
    private final NotificationPublisher notificationPublisher;
    // 발행 연동(초안 확인·삭제)용 — 첨부 소유 이전은 AttachmentService 가 담당한다
    private final DraftMapper draftMapper;

    /**
     * 게시판 카테고리 목록 (카테고리 미사용 게시판은 빈 목록).
     *
     * 요청 파라미터를 받지 않는다 — 토큰의 사용자로 관리자 여부를 판정해
     * 관리자에게만 '중요'(code=PINNED)를 포함시킨다. 프론트는 받은 목록을 그대로 그리면 된다.
     */
    @Override
    public List<CategoryResponse> getCategoryList(Long boardId) {
        BoardPolicy policy = boardPolicyService.requireReadable(boardId);
        if (!policy.isCategoryUsed()) {
            return List.of();
        }
        return boardMapper.findCategoriesByBoardId(boardId, AuthUtils.isAdmin());
    }

    // 메인용 상단 N개 조회 (개수는 프론트가 정한다)
    @Override
    public List<BoardTopPostResponse> getTopPosts(Long boardId, int num) {
        boardPolicyService.requireReadable(boardId);
        if (num < 1 || num > MAX_TOP_POSTS) {
            throw new BoardException("조회 개수는 1 이상 " + MAX_TOP_POSTS + " 이하여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        return boardMapper.findTopPosts(boardId, num);
    }

    // 게시판 전체 목록 조회
    @Override
    public BoardPageResponse getPostList(Long boardId, int page, int size, Long categoryId, String keyword, String sort) {
        boardPolicyService.requireReadable(boardId);

        // 보정하지 않으면 ?size=-1 이 LIMIT -1 로, 큰 page 는 넘친 OFFSET 음수로 나가 둘 다 500 이 된다
        page = PageUtils.clampPage(page);
        size = PageUtils.clampSize(size);

        int totalCount = boardMapper.countPosts(boardId, categoryId, keyword);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        // 글이 없는 게시판도 정상 상태다 (새로 만든 게시판은 항상 0건) → 빈 목록으로 응답
        List<BoardListResponse> posts = totalCount == 0
                ? List.of()
                : boardMapper.findAllPosts(boardId, categoryId, PageUtils.offset(page, size), size, keyword, sort);

        BoardPageResponse response = new BoardPageResponse();
        response.setTotalPages(totalPages);
        response.setTotalCount(totalCount);
        response.setPosts(posts);
        return response;
    }

    // 게시글 단일 상세 조회 (조회수 증가, 첨부/좋아요/댓글 수 포함 — 댓글 본문은 getComments 별도 API)
    @Override
    @Transactional
    public BoardDetailResponse getPostDetail(Long boardId, Long postId, String sort) {
        return buildDetail(boardId, postId, sort);
    }

    // 상세 응답 조립
    private BoardDetailResponse buildDetail(Long boardId, Long postId, String sort) {
        boardPolicyService.requireReadable(boardId);
        Long currentUserId = AuthUtils.currentUserId();

        BoardDetailResponse detail = boardMapper.findPostDetailById(postId, boardId, sort);
        if (detail == null) {
            throw new BoardException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }

        // 이전글/다음글: id만 뽑아온 뒤 제목·카테고리·작성일을 채운다 (하단 내비게이션용)
        if (detail.getPrevPostId() != null) {
            detail.setPrevPost(boardMapper.findAdjacentPost(detail.getPrevPostId()));
        }
        if (detail.getNextPostId() != null) {
            detail.setNextPost(boardMapper.findAdjacentPost(detail.getNextPostId()));
        }

        boardMapper.updateViewCount(postId);
        detail.setViewCount(detail.getViewCount() + 1); // 방금 올린 값을 응답에 반영

        List<AttachmentFileResponse> attachments = boardMapper.findAttachmentsByPostId(postId);
        detail.setAttachments(attachments);
        detail.setAttachmentCount(attachments != null ? attachments.size() : 0);
        detail.setLikeCount(boardMapper.countLikesByPostId(postId));
        // 댓글 본문은 상세에 싣지 않는다 (별도 API). 화면 헤더용 개수만 채운다
        detail.setCommentCount(boardMapper.countCommentsByPostId(postId));
        detail.setIsLiked(boardMapper.existsLikeByPostIdAndUserId(postId, currentUserId));

        // 익명 글 작성자 마스킹 — 마스킹은 서버 책임 (프론트 마스킹은 API 직접 호출로 우회된다)
        // 관리자와 작성자 본인에게만 실작성자를 보여준다.
        if (Boolean.TRUE.equals(detail.getIsAnonymous())
                && !AuthUtils.isAdmin() && !currentUserId.equals(detail.getUserId())) {
            detail.setAuthorName("익명");
            detail.setUserId(null);
        }

        return detail;
    }

    /**
     * 게시글의 댓글 목록 (상세와 분리된 API).
     *
     * 매퍼가 state='normal' 만 조회하므로 관리자가 블라인드했거나 삭제 처리한 댓글,
     * 작성자가 지운 댓글은 학생 화면에 내려가지 않는다.
     * 비밀댓글 열람 판정에 원글 작성자가 필요해 여기서 원글 작성자 id를 함께 조회한다.
     */
    @Override
    public List<CommentDetailResponse> getComments(Long boardId, Long postId) {
        boardPolicyService.requireReadable(boardId);
        requirePostInBoard(postId, boardId); // 타 게시판 글·블라인드/삭제 글의 댓글 열람 차단
        Long postAuthorId = boardMapper.findAuthorIdByPostId(postId);
        return maskComments(boardMapper.findCommentsByPostId(postId), postAuthorId, AuthUtils.currentUserId());
    }

    /**
     * 댓글 목록 서버측 마스킹.
     *  - 비밀댓글: 관리자 / 댓글 작성자 / 원글 작성자만 내용 열람, 그 외에는 내용을 가린다
     *  - 익명댓글: 관리자 / 댓글 작성자 외에는 실명·userId를 가린다
     */
    private List<CommentDetailResponse> maskComments(List<CommentDetailResponse> comments,
                                                     Long postAuthorId, Long currentUserId) {
        boolean admin = AuthUtils.isAdmin();
        boolean postAuthor = currentUserId.equals(postAuthorId);
        for (CommentDetailResponse comment : comments) {
            boolean commentAuthor = currentUserId.equals(comment.getUserId());
            if (Boolean.TRUE.equals(comment.getIsPrivate()) && !admin && !commentAuthor && !postAuthor) {
                comment.setContent("비밀댓글입니다.");
            }
            if (Boolean.TRUE.equals(comment.getIsAnonymous()) && !admin && !commentAuthor) {
                comment.setAuthorName("익명");
                comment.setUserId(null);
            }
        }
        return comments;
    }

    // 게시글 좋아요 토글
    @Override
    @Transactional
    public BoardResponse togglePostLike(Long boardId, Long postId) {
        boardPolicyService.requireReadable(boardId);
        requirePostInBoard(postId, boardId);
        Long userId = AuthUtils.currentUserId();

        if (boardMapper.existsLikeByPostIdAndUserId(postId, userId)) {
            boardMapper.deleteLike(postId, userId);
            return new BoardResponse("좋아요를 취소했습니다.");
        }
        boardMapper.insertLike(postId, userId);
        return new BoardResponse("좋아요를 눌렀습니다.");
    }

    // 게시글 신규 등록 (파일 업로드 포함)
    @Override
    @Transactional
    public BoardResponse createPost(Long boardId, BoardRequest request) {
        BoardPolicy policy = boardPolicyService.requireWritable(boardId);
        Long userId = AuthUtils.currentUserId();

        applyWritePolicy(policy, request);
        boolean pinned = resolvePinned(policy, request.getCategoryId());

        boardMapper.insertPost(request, userId, boardId, pinned);
        saveAttachments(policy, userId, request.getPostId(), request.getFiles());

        // 선업로드 연결·초안 이관은 게시판이 파일 업로드를 허용할 때만 — 다른 게시판에서 올린
        // attachmentId 를 첨부 금지 게시판에 실어 보내는 정책 우회를 막는다(saveAttachments 와 같은 게이트)
        if (policy.isAttachmentAllowed()) {
            // 임시저장에서 발행한 경우: 초안 첨부를 먼저 이 게시글로 이관하고 초안을 삭제한다(같은 트랜잭션).
            // linkToPost 보다 먼저 실행해 초안 첨부가 "이 글의 것"이 된 상태에서 연결·정리가 이어지게 한다
            publishFromDraftIfPresent(request.getDraftId(), boardId, userId, request.getPostId());

            // 에디터에서 미리 올려 둔 파일(본문 이미지·첨부)을 이 글의 것으로 만든다.
            // 본문 마크다운에 남아 있는 /api/user/files/{id} 도 함께 연결된다 — 연결되지 않은 파일은 정리 배치가 지운다
            attachmentService.linkToPost(request.getPostId(), request.getAttachmentIds(), request.getContent());

            // 연결됐지만 발행 본문에는 없는 인라인 이미지 정리 — 초안 이관분이든 attachmentIds 에
            // 습관적으로 실려 온 id 든, "마크다운이 기준" 규칙 하나로 정리한다
            attachmentService.syncInlineAttachments(request.getPostId(), request.getContent());
        }

        // 생성 PK 반환 — 프론트가 등록 직후 상세 페이지로 이동하는 데 필요
        return new BoardResponse("게시글이 성공적으로 등록되었습니다.", request.getPostId());
    }

    /**
     * 임시저장 → 발행 연동.
     *
     * 순서 절대 중요(SPEC-A5 §6-3): <b>첨부 소유권 이관(UPDATE) 먼저, 초안 삭제(DELETE) 나중.</b>
     * 순서를 바꾸면 fk_attachments_draft 의 ON DELETE CASCADE 가 방금 발행한 글의 첨부를 통째로 지운다.
     * 단일 UPDATE(post_id 세팅 + draft_id 비움)라 CHECK(동시 소유 금지)도 만족한다.
     *
     * 없는/남의/다른 게시판 초안이면 조용히 무시된다 — 발행 자체를 막을 이유가 없기 때문(SPEC-A5 §2-6).
     */
    private void publishFromDraftIfPresent(Long draftId, Long boardId, Long userId, Long postId) {
        if (draftId == null) {
            return;
        }
        // 본인 + 이 게시판의 초안인지 확인 겸 drafts 행 잠금 — 다른 게시판의 초안 id 는 무시하고,
        // 락 순서(drafts → attachments)를 저장·삭제 경로와 맞춰 동시 저장·삭제와의 경합을 직렬화한다
        if (draftMapper.lockDraft(draftId, userId, boardId) == null) {
            log.info("발행 draftId 무시(없음/남의 것/다른 게시판) - draftId: {}, userId: {}", draftId, userId);
            return;
        }
        attachmentService.transferDraftToPost(draftId, postId); // ① 첨부 이관 먼저
        draftMapper.deleteDraft(draftId, userId);               // ② 초안 삭제 나중
    }

    // 발행 시점에 함께 올라온 첨부 저장 (등록·수정 공통). 첨부를 쓰지 않는 게시판이면 조용히 무시한다
    // 저장 경로는 uploads/board-{boardId}/{postId}/원본파일명 — 글 단위 폴더라 글끼리 이름이 겹칠 일이 없다
    // (선업로드분은 글 번호를 모르는 시점에 저장되므로 uploads/board-{boardId}/user-{userId}/ 에 들어간다)
    private void saveAttachments(BoardPolicy policy, Long uploaderId, Long postId, List<MultipartFile> files) {
        if (!policy.isAttachmentAllowed() || CollectionUtils.isEmpty(files)) {
            return;
        }
        String dir = policy.uploadDir() + "/" + postId;
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String savedPath = fileStorageUtil.save(file, dir);
            boardMapper.insertAttachment(postId, uploaderId, file.getOriginalFilename(), savedPath,
                    file.getSize(), file.getContentType());
        }
    }

    // 게시글 수정: 관리자이거나 작성자 본인일 경우 가능
    @Override
    @Transactional
    public BoardResponse updatePost(Long boardId, Long postId, BoardUpdateRequest request) {
        BoardPolicy policy = boardPolicyService.requireReadable(boardId);
        requirePostInBoard(postId, boardId);
        Long currentUserId = AuthUtils.currentUserId();
        Long authorId = boardMapper.findAuthorIdByPostId(postId);

        if (!AuthUtils.isAdmin() && !currentUserId.equals(authorId)) {
            throw new BoardException("수정 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        // 카테고리/익명은 등록과 동일한 정책으로 보정하고, 상단 고정은 카테고리에서 다시 판정한다
        // (중요 → 일반 카테고리로 바꾸면 고정이 자동 해제된다)
        request.setCategoryId(resolveCategoryId(policy, request.getCategoryId()));
        if (!policy.isAnonymousAllowed()) {
            request.setIsAnonymous(false);
        }
        boolean pinned = resolvePinned(policy, request.getCategoryId());

        int updated = boardMapper.updatePost(postId, request, pinned);
        if (updated == 0) {
            // state != normal (블라인드/삭제) 이거나 존재하지 않는 글
            throw new BoardException("수정할 수 없는 게시글입니다.", HttpStatus.NOT_FOUND);
        }

        // 첨부는 증분 처리: 지운 것만 소프트삭제하고, 새로 올린 것만 추가한다
        if (!CollectionUtils.isEmpty(request.getDeleteAttachmentIds())) {
            // 물리 파일까지 지워 고아 파일이 디스크에 남지 않게 한다 (PM 결정 2026-08-16).
            // 삭제할 경로를 먼저 확보해 행을 소프트삭제하되, 파일은 **커밋 후** 지운다 —
            // 이후 단계(새 첨부 저장 등)가 실패해 롤백되면 행은 normal 로 살아나는데
            // 파일만 사라진 "정상 첨부인데 다운로드 404" 상태가 되기 때문이다.
            // 대상 글 소속인 것만 조회/삭제되므로 남의 첨부 id가 섞여도 무해하다
            List<String> fileUrls = boardMapper.findAttachmentUrls(postId, request.getDeleteAttachmentIds());
            boardMapper.softDeleteAttachments(postId, request.getDeleteAttachmentIds());
            fileStorageUtil.deleteAfterCommit(fileUrls);
        }
        saveAttachments(policy, currentUserId, postId, request.getFiles());
        if (policy.isAttachmentAllowed()) {
            // 수정 중 새로 선업로드한 파일 연결 → 그다음 본문에서 사라진 인라인 이미지 정리.
            // 순서가 중요하다: 연결을 먼저 해야 방금 넣은 이미지가 "본문에 없는 이미지"로 오인되지 않는다
            attachmentService.linkToPost(postId, request.getAttachmentIds(), request.getContent());
            attachmentService.syncInlineAttachments(postId, request.getContent());
        }
        // 응답은 message 만 — 어차피 프론트가 상세로 이동하며 GET 을 한 번 더 하므로 상세 객체 반환은 낭비 (PM 합의)
        return new BoardResponse("게시글이 성공적으로 수정되었습니다.");
    }

    /**
     * 게시글 삭제 (소프트). 작성자 본인만 가능하다.
     * 관리자의 타인 글 삭제는 이 API가 아니라 관리자 API(/api/admin/posts/{postId})를 쓴다 —
     * 그쪽만 ModerationService를 경유해 moderation_log 기록과 벌점 부과가 일어나기 때문.
     * 여기서 관리자를 허용하면 로그·벌점 없는 우회 삭제 경로가 생긴다.
     */
    @Override
    @Transactional
    public BoardResponse deletePost(Long boardId, Long postId) {
        boardPolicyService.requireReadable(boardId);
        requirePostInBoard(postId, boardId);
        Long currentUserId = AuthUtils.currentUserId();

        Long authorId = boardMapper.findAuthorIdByPostId(postId);
        if (!currentUserId.equals(authorId)) {
            throw new BoardException("본인 글만 삭제할 수 있습니다. (관리자 조치는 관리자 게시글 관리에서)", HttpStatus.FORBIDDEN);
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
        requirePostInBoard(postId, boardId); // 타 게시판 글·블라인드/삭제 글에 대한 댓글 차단
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

        // 댓글/답글 알림 발행 (부가기능 — 실패해도 댓글 등록은 성공해야 하므로 내부에서 예외를 삼킨다)
        publishCommentNotifications(boardId, postId, userId, parentCommentId);

        return new CommentResponse("댓글이 성공적으로 등록되었습니다.");
    }

    /**
     * 댓글/답글 알림 발행.
     *
     * 수신자 규칙(확정):
     *  - 일반 댓글  → 글 작성자에게 COMMENT
     *  - 답글       → 부모 댓글 작성자에게 REPLY + 글 작성자에게 COMMENT (답글도 그 글에 달린 반응이므로)
     *  - 같은 사람이 둘 다면 REPLY 하나만 (더 구체적인 쪽 우선)
     *  - 어떤 경우든 <b>지금 댓글을 단 본인에게는 발행하지 않는다</b>
     *
     * 알림은 부가기능이라 발행 실패가 본 기능(댓글 등록)을 롤백·500 시키면 안 된다 → 전체를 try/catch 로 감싸 로그만 남긴다.
     * (외부 HTTP 푸시는 NotificationPushService 가 @Async 로 이미 분리되어 있어 지연·실패가 이 흐름에 영향을 주지 않는다.)
     */
    private void publishCommentNotifications(Long boardId, Long postId, Long actorId, Long parentCommentId) {
        try {
            // 수신자 → 알림유형. 삽입 순서(REPLY 먼저)를 유지해 동일인 중복 시 putIfAbsent 가 REPLY 를 남기게 한다.
            Map<Long, NotificationType> recipients = new LinkedHashMap<>();
            if (parentCommentId != null) {
                Long parentAuthorId = boardMapper.findCommentAuthorId(parentCommentId);
                if (parentAuthorId != null) {
                    recipients.put(parentAuthorId, NotificationType.REPLY);
                }
            }
            Long postAuthorId = boardMapper.findAuthorIdByPostId(postId);
            if (postAuthorId != null) {
                recipients.putIfAbsent(postAuthorId, NotificationType.COMMENT); // 이미 REPLY 로 잡혔으면 유지
            }
            recipients.remove(actorId); // 본인 제외 (내 글/내 댓글에 내가 달아도 나에겐 안 감)

            recipients.forEach((receiverId, type) ->
                    notificationPublisher.publish(receiverId, type, "post", postId, boardId));
        } catch (Exception e) {
            log.warn("댓글 알림 발행 실패 - postId: {}, parentCommentId: {}, {}", postId, parentCommentId, e.getMessage());
        }
    }

    // 댓글 수정: 관리자이거나 작성자 본인일 경우 가능
    @Override
    @Transactional
    public CommentResponse updateComment(Long boardId, Long commentId, CommentRequest request) {
        BoardPolicy policy = boardPolicyService.requireReadable(boardId);
        requireCommentInBoard(commentId, boardId);
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

    // 댓글 삭제 (소프트): 작성자 본인만. 관리자 조치는 admin.moderation 경유(로그+벌점) — deletePost와 동일한 이유
    @Override
    @Transactional
    public BoardResponse deleteComment(Long boardId, Long commentId) {
        boardPolicyService.requireReadable(boardId);
        requireCommentInBoard(commentId, boardId);
        Long currentUserId = AuthUtils.currentUserId();
        Long authorId = boardMapper.findCommentAuthorId(commentId);

        if (!currentUserId.equals(authorId)) {
            throw new BoardException("본인 댓글만 삭제할 수 있습니다. (관리자 조치는 관리자 화면에서)", HttpStatus.FORBIDDEN);
        }

        boardMapper.deleteComment(commentId);
        return new BoardResponse("댓글이 성공적으로 삭제되었습니다.");
    }

    // ---- 내부 헬퍼 ----

    // 게시글이 URL의 게시판 소속 + normal 상태인지 확인 (read_scope 우회·블라인드/삭제 글 조작 차단)
    // 타 게시판 글이면 대상의 존재 자체를 노출하지 않기 위해 403이 아니라 404로 응답한다
    private void requirePostInBoard(Long postId, Long boardId) {
        if (!boardMapper.existsNormalPostInBoard(postId, boardId)) {
            throw new BoardException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }
    }

    // 댓글 버전 동일 가드
    private void requireCommentInBoard(Long commentId, Long boardId) {
        if (!boardMapper.existsNormalCommentInBoard(commentId, boardId)) {
            throw new BoardException("존재하지 않는 댓글입니다.", HttpStatus.NOT_FOUND);
        }
    }

    // 등록 요청에 게시판 정책을 적용 (카테고리 보정, 익명 허용 여부)
    private void applyWritePolicy(BoardPolicy policy, BoardRequest request) {
        request.setCategoryId(resolveCategoryId(policy, request.getCategoryId()));
        if (!policy.isAnonymousAllowed()) {
            request.setIsAnonymous(false);
        }
    }

    /**
     * 카테고리 보정.
     * 카테고리 미사용 게시판은 null, 이 게시판에 없는 값이면 게시판 기본값으로 대체한다.
     * '중요'는 관리자만 고를 수 있으므로, 일반 회원이 강제로 보내면 기본값으로 되돌린다
     * (자동으로 상단 고정도 풀린다 — isPinned 판정이 카테고리에서 파생되기 때문).
     */
    private Long resolveCategoryId(BoardPolicy policy, Long requestedCategoryId) {
        if (!policy.isCategoryUsed()) {
            return null;
        }
        if (requestedCategoryId == null || !boardMapper.existsCategory(requestedCategoryId, policy.getBoardId())) {
            return policy.getDefaultCategoryId();
        }
        if (!AuthUtils.isAdmin() && boardMapper.isPinnedCategory(requestedCategoryId, policy.getBoardId())) {
            log.debug("일반 회원의 '중요' 카테고리 선택 무시 - userId: {}", AuthUtils.currentUserIdOrNull());
            return policy.getDefaultCategoryId();
        }
        return requestedCategoryId;
    }

    /**
     * 상단 고정 여부 판정.
     * 요청 필드가 아니라 "선택한 카테고리가 이 게시판의 '중요'(code=PINNED)인가"로 결정한다.
     * is_pinned 컬럼을 그대로 두는 이유는 목록 정렬(ORDER BY is_pinned DESC)에서 조인 없이 쓰기 위함.
     */
    private boolean resolvePinned(BoardPolicy policy, Long categoryId) {
        return categoryId != null
                && AuthUtils.isAdmin()
                && boardMapper.isPinnedCategory(categoryId, policy.getBoardId());
    }

}
