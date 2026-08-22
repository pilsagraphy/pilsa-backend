package com.back.mypage.profile.dto;

import java.time.LocalDateTime;
import lombok.Getter;

// 마이페이지 상단 화면 (프로필 + 전체 기간 활동 수 + 이번 학기 활동 요약)
@Getter
public class MyPageSummaryResponse {
    private final String loginId;
    private final String name;
    private final LocalDateTime joinedAt;
    private final int postCount;         // 전체 기간 작성글 수
    private final int commentCount;      // 전체 기간 작성한 댓글 수
    private final int likedCount;        // 전체 기간 좋아요 누른 글 수
    private final SemesterActivityResponse semester;

    public MyPageSummaryResponse(String loginId, String name, LocalDateTime joinedAt,
                                  int postCount, int commentCount, int likedCount,
                                  SemesterActivityResponse semester) {
        this.loginId = loginId;
        this.name = name;
        this.joinedAt = joinedAt;
        this.postCount = postCount;
        this.commentCount = commentCount;
        this.likedCount = likedCount;
        this.semester = semester;
    }
}
