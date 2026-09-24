package com.back.mypage.profile.dto;

import lombok.Getter;

// 이번 학기 활동 요약 (작성한 글/댓글, 받은 좋아요) — 학기 경계는 policy_settings로 설정
@Getter
public class SemesterActivityResponse {
    private final int posts;
    private final int comments;
    private final int receivedLikes;

    public SemesterActivityResponse(int posts, int comments, int receivedLikes) {
        this.posts = posts;
        this.comments = comments;
        this.receivedLikes = receivedLikes;
    }
}
