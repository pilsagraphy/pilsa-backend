package com.back.student.info.mapper;

import com.back.student.info.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InfoMapper {

    // 정보게시판 카테고리 목록 조회
    List<CategoryResponse> findCategoriesByBoardId(@Param("boardId") Long boardId);

    // 메인 화면용 최신글 5개 조회
    List<InfoTop5Response> findTop5Posts(@Param("boardId") Long boardId);

    // 게시글 전체 목록 조회 (페이징, 카테고리 필터, 검색, 정렬 포함)
    List<InfoListResponse> findAllPosts(
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
    InfoDetailResponse findPostDetailById(
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

    // 게시글 등록 (익명 필드 제외)
    void insertPost(@Param("request") InfoRequest request, @Param("userId") Long userId, @Param("boardId") Long boardId);

    // 게시글 수정 및 삭제 권한 확인을 위한 작성자 ID 조회
    Long findAuthorIdByPostId(@Param("postId") Long postId);

    // 게시글 수정 (익명 필드 제외)
    int updatePost(@Param("postId") Long postId, @Param("request") InfoUpdateRequest request);

    // 게시글 삭제
    int deletePost(@Param("postId") Long postId);

    // 관리자 강제 삭제 (소프트 삭제 - state만 'deleted'로 전환)
    int softDeletePost(@Param("postId") Long postId);

    // 첨부파일 정보 DB 등록
    void insertAttachment(
            @Param("postId") Long postId,
            @Param("originName") String originName,
            @Param("fileUrl") String fileUrl,
            @Param("fileSize") Long fileSize,
            @Param("fileType") String fileType
    );

    // --- 댓글 관련 메서드 ---

    // 게시글에 달린 댓글 목록 조회
    List<CommentDetailResponse> findCommentsByPostId(@Param("postId") Long postId);

    // 댓글 등록 (익명 제외, 비밀댓글 여부 추가)
    void insertComment(@Param("postId") Long postId, @Param("userId") Long userId, @Param("request") CommentRequest request);

    // 댓글 수정 및 삭제 권한 확인을 위한 작성자 ID 조회
    Long findCommentAuthorId(@Param("commentId") Long commentId);

    // 댓글 수정 (익명 제외, 비밀댓글 여부 추가)
    void updateComment(@Param("commentId") Long commentId, @Param("request") CommentRequest request);

    // 댓글 삭제
    void deleteComment(@Param("commentId") Long commentId);

    // 관리자 강제 삭제 (소프트 삭제 - state만 'deleted'로 전환)
    int softDeleteComment(@Param("commentId") Long commentId);
}