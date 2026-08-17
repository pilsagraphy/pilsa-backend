package com.back.board.mapper;

import com.back.board.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 게시판(공지/자유/정보) 통합 매퍼.
 * 모든 조회/등록은 boardId(posts.board_id)로 게시판을 구분한다.
 * post_id / comment_id 는 posts / comments 테이블에서 전역 유니크하므로 단건 처리는 별도 boardId 없이 동작한다.
 */
@Mapper
public interface BoardMapper {

    // --- 게시판 정책 (boards 테이블) — BoardType enum 대체 ---

    // 게시판 1건의 정책 조회 (삭제된 게시판은 null)
    BoardPolicy findBoardPolicy(@Param("boardId") Long boardId);

    // 게시판 목록 (사이드바 / 관리자 화면 공용). 삭제된 게시판 제외, display_order 순
    List<BoardPolicy> findBoardPolicies();

    /**
     * 게시판 카테고리 목록.
     * includePinned=false 면 '중요'(code=PINNED)를 제외한다 — 일반 회원에게는 내려가지 않는다.
     */
    List<CategoryResponse> findCategoriesByBoardId(@Param("boardId") Long boardId,
                                                   @Param("includePinned") boolean includePinned);

    // 해당 카테고리가 이 게시판에 실제로 존재하는지 확인 (등록 시 유효성 검사용)
    boolean existsCategory(@Param("categoryId") Long categoryId, @Param("boardId") Long boardId);

    // 선택한 카테고리가 이 게시판의 '중요'(code=PINNED) 카테고리인가 → is_pinned 판정용
    boolean isPinnedCategory(@Param("categoryId") Long categoryId, @Param("boardId") Long boardId);

    // 이전글/다음글 상세 (카테고리 뱃지·제목·작성일 표시용)
    AdjacentPostResponse findAdjacentPost(@Param("postId") Long postId);

    /** 상단 N개 (개수는 프론트 요청값). is_pinned 우선 정렬 */
    List<BoardTopPostResponse> findTopPosts(@Param("boardId") Long boardId, @Param("limit") int limit);

    // 게시글이 URL의 게시판 소속 + 노출(normal) 상태인지 — 좋아요/댓글/수정/삭제 공통 가드.
    // 이 검증이 없으면 열람 가능한 게시판 URL에 타 게시판 postId를 넣어 read_scope를 우회할 수 있다.
    boolean existsNormalPostInBoard(@Param("postId") Long postId, @Param("boardId") Long boardId);

    // 댓글이 URL의 게시판 게시글에 달린 normal 댓글인지 — 댓글 수정/삭제 가드
    boolean existsNormalCommentInBoard(@Param("commentId") Long commentId, @Param("boardId") Long boardId);

    // 게시글 전체 목록 조회 (페이징, 카테고리 필터, 검색, 정렬 포함)
    List<BoardListResponse> findAllPosts(
            @Param("boardId") Long boardId,
            @Param("categoryId") Long categoryId,
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("keyword") String keyword,
            @Param("sort") String sort
    );

    // 게시글 총 개수 조회 (페이징 계산용)
    int countPosts(
            @Param("boardId") Long boardId,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword
    );

    // 게시글 단일 상세 조회 (이전글/다음글 포함)
    BoardDetailResponse findPostDetailById(
            @Param("postId") Long postId,
            @Param("boardId") Long boardId,
            @Param("sort") String sort
    );

    // 게시글 조회수 증가
    void updateViewCount(@Param("postId") Long postId);

    // 게시글에 포함된 첨부파일 리스트 조회
    List<AttachmentFileResponse> findAttachmentsByPostId(@Param("postId") Long postId);

    // 게시글 좋아요 총 개수 조회
    int countLikesByPostId(@Param("postId") Long postId);

    // 특정 유저의 게시글 좋아요 여부 확인
    boolean existsLikeByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    // 좋아요 추가
    void insertLike(@Param("postId") Long postId, @Param("userId") Long userId);

    // 좋아요 취소
    void deleteLike(@Param("postId") Long postId, @Param("userId") Long userId);

    // 게시글 등록. isPinned 는 요청값이 아니라 서비스가 카테고리('중요')로 판정한 결과다
    void insertPost(@Param("request") BoardRequest request,
                    @Param("userId") Long userId,
                    @Param("boardId") Long boardId,
                    @Param("isPinned") boolean isPinned);

    // 게시글 수정 및 삭제 권한 확인을 위한 작성자 ID 조회
    Long findAuthorIdByPostId(@Param("postId") Long postId);

    // 게시글 수정. isPinned 는 카테고리로 재판정된 값 (중요 → 일반 카테고리로 바꾸면 자동 해제)
    int updatePost(@Param("postId") Long postId,
                   @Param("request") BoardUpdateRequest request,
                   @Param("isPinned") boolean isPinned);

    // 게시글 삭제
    int deletePost(@Param("postId") Long postId);

    // 첨부 소프트삭제 (수정 화면에서 X 누른 것). 대상 글 소속인 것만 지워진다
    int softDeleteAttachments(@Param("postId") Long postId, @Param("attachmentIds") List<Long> attachmentIds);

    /** 삭제 대상 첨부의 물리 경로 조회 (소프트삭제 전에 확보 → 디스크 고아 파일 방지) */
    List<String> findAttachmentUrls(@Param("postId") Long postId, @Param("attachmentIds") List<Long> attachmentIds);

    // 첨부파일 정보 DB 등록
    void insertAttachment(
            @Param("postId") Long postId,
            @Param("originName") String originName,
            @Param("fileUrl") String fileUrl,
            @Param("fileSize") Long fileSize,
            @Param("fileType") String fileType
    );

    // --- 댓글 관련 메서드 (자유/정보게시판) ---

    // 게시글에 달린 댓글 목록 조회
    List<CommentDetailResponse> findCommentsByPostId(@Param("postId") Long postId);

    /** 노출 대상(state=normal) 댓글 수 — 상세 응답의 commentCount */
    int countCommentsByPostId(@Param("postId") Long postId);

    // 대댓글 등록 시 부모 댓글이 같은 게시글에 실제로 존재하는지 확인
    boolean existsCommentInPost(@Param("commentId") Long commentId, @Param("postId") Long postId);

    // 댓글 등록
    void insertComment(@Param("postId") Long postId, @Param("userId") Long userId, @Param("request") CommentRequest request);

    // 댓글 수정 및 삭제 권한 확인을 위한 작성자 ID 조회
    Long findCommentAuthorId(@Param("commentId") Long commentId);

    // 댓글 수정
    void updateComment(@Param("commentId") Long commentId, @Param("request") CommentRequest request);

    // 댓글 삭제
    void deleteComment(@Param("commentId") Long commentId);
}
