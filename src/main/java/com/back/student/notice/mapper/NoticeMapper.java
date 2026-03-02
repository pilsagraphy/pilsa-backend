package com.back.student.notice.mapper;

import com.back.student.notice.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NoticeMapper {

    /**
     * 공지사항 전체 목록 조회
     * ERD의 posts, users, comments, post_likes, attachments 테이블을 조인하여
     * 요청받은 모든 정보(글쓴이, 댓글수, 좋아요수 등)를 가져옴
     */
    List<NoticeListResponse> findAllNotices(
            @Param("boardId") Long boardId,
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("keyword") String keyword,
            @Param("sort") String sort // 명세서의 sort=created 등 정렬 조건
    );

    // 전체 페이지(totalPages) 계산을 위한 게시글 총 개수 조회
    int countNotices(
            @Param("boardId") Long boardId,
            @Param("keyword") String keyword
    );
    // 메인 화면용 상단 5개 조회
    List<NoticeTop5Response> findTop5Notices(@Param("boardId") Long boardId);

    // 공지사항 단일글 조회
    void updateViewCount(@Param("postId") Long postId);
    NoticeDetailResponse findNoticeDetailById(
            @Param("postId") Long postId,
            @Param("boardId") Long boardId,
            @Param("sort") String sort
    );
    List<AttachmentFileResponse> findAttachmentIdsByPostId(@Param("postId") Long postId);
    // 게시글 전체 좋아요 개수 조회
    int countLikesByPostId(@Param("postId") Long postId);
    // 특정 유저의 좋아요 여부 확인
    boolean existsLikeByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    // 좋아요
    void insertLike(@Param("postId") Long postId, @Param("userId") Long userId);
    int deleteLike(@Param("postId") Long postId, @Param("userId") Long userId);

    // 공지사항 등록 수정 삭제
    void insertNotice(@Param("request") NoticeRequest request, @Param("userId") Long userId, @Param("boardId") Long boardId);
    int updateNotice(@Param("postId") Long postId, @Param("request") NoticeUpdateRequest request);
    int deleteNotice(@Param("postId") Long postId);
    void insertAttachment(@Param("postId") Long postId,
                          @Param("originName") String originName,
                          @Param("fileUrl") String fileUrl,
                          @Param("fileSize") Long fileSize,
                          @Param("fileType") String fileType);
}
