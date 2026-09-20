package com.back.admin.moderation.revision;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 글·댓글 본문 스냅샷(content_revisions).
 *
 * 스냅샷은 대상 표(posts/comments)에서 그 순간의 제목·본문을 그대로 복사해 넣는다 —
 * 호출하는 쪽이 본문을 들고 다니지 않아도 되고, 잘못된 본문을 넣을 길도 없다.
 */
@Mapper
public interface ContentRevisionMapper {

    /** 글의 지금 제목·본문을 한 줄로 남긴다 */
    int snapshotPost(@Param("postId") Long postId,
                     @Param("trigger") String trigger,
                     @Param("savedBy") Long savedBy);

    /** 댓글의 지금 본문을 한 줄로 남긴다 */
    int snapshotComment(@Param("commentId") Long commentId,
                        @Param("trigger") String trigger,
                        @Param("savedBy") Long savedBy);

    /** 글 하나의 스냅샷 (오래된 것부터) */
    List<ContentRevisionResponse> findByPost(@Param("postId") Long postId);

    /** 이 글에 달린 댓글들의 스냅샷 전부 (오래된 것부터) */
    List<ContentRevisionResponse> findByPostComments(@Param("postId") Long postId);
}
