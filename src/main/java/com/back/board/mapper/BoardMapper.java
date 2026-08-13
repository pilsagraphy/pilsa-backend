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

    // 게시판 목록 (관리자 화면 / 사이드바). 삭제된 게시판 제외, display_order 순
    List<BoardPolicy> findBoardPolicies();

    // 게시판별 게시글 수 (관리자 게시판 관리 화면)
    int countPostsByBoard(@Param("boardId") Long boardId);

    // 게시판 생성
    void insertBoard(@Param("board") BoardPolicy board);

    // 게시판 수정 (전달된 필드만)
    int updateBoard(@Param("boardId") Long boardId, @Param("board") BoardPolicy board);

    // 게시판 소프트삭제
    int deleteBoard(@Param("boardId") Long boardId);

    // 게시판명 중복 확인 (본인 제외)
    boolean existsBoardName(@Param("name") String name, @Param("excludeBoardId") Long excludeBoardId);

    // 게시판 카테고리 목록 조회 (공지사항은 데이터가 없어 빈 목록 반환)
    List<CategoryResponse> findCategoriesByBoardId(@Param("boardId") Long boardId);

    // 해당 카테고리가 이 게시판에 실제로 존재하는지 확인 (등록 시 유효성 검사용)
    boolean existsCategory(@Param("categoryId") Long categoryId, @Param("boardId") Long boardId);

    // 메인 화면용 상단 5개 조회 (공지사항은 is_pinned 우선 정렬)
    List<BoardTop5Response> findTop5Posts(@Param("boardId") Long boardId);

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

    // 게시글 등록
    void insertPost(@Param("request") BoardRequest request, @Param("userId") Long userId, @Param("boardId") Long boardId);

    // 게시글 수정 및 삭제 권한 확인을 위한 작성자 ID 조회
    Long findAuthorIdByPostId(@Param("postId") Long postId);

    // 게시글 수정
    int updatePost(@Param("postId") Long postId, @Param("request") BoardUpdateRequest request);

    // 게시글 삭제
    int deletePost(@Param("postId") Long postId);

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
