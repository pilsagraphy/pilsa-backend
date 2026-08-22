package com.back.mypage.profile.controller;

import com.back.mypage.profile.dto.MyPageSummaryResponse;
import com.back.mypage.profile.service.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-프로필",
        description = "마이페이지 상단 화면 — 전체 기간 활동 수(작성글/작성한 댓글/좋아요 누른 글) + 이번 학기 활동 요약(작성한 글/작성한 댓글/받은 좋아요). " +
                "학기 기준월은 policy_settings(semester1_start_month/semester2_start_month)로 설정한다.")
public class MyPageProfileController {

    private final MyPageService myPageService;

    @Operation(summary = "마이페이지 프로필/활동 요약", description = """
            마이페이지 진입 시 호출한다.

            ### 응답 예시
            ```json
            {
              "loginId": "pilsagraphy",
              "name": "김본명",
              "joinedAt": "2026-07-14T10:00:00",
              "postCount": 24,
              "commentCount": 87,
              "likedCount": 16,
              "semester": { "posts": 3, "comments": 10, "receivedLikes": 5 }
            }
            ```
            - postCount/commentCount/likedCount: 전체 기간, 블라인드·삭제 글/댓글은 제외(state='normal').
            - semester: 이번 학기(policy_settings semester1_start_month/semester2_start_month, 기본 3월/9월 시작) 기준
              작성한 글·댓글 수와, 이번 학기 동안 내 글이 받은 좋아요 수.
              receivedLikes 는 post_likes.created_at(좋아요를 누른 시점) 기준이다 —
              글 작성 시점으로 세면 지난 학기 글이 이번 학기에 받은 좋아요가 누락된다.""")
    @GetMapping("/api/user/mypage")
    public ResponseEntity<MyPageSummaryResponse> getSummary() {
        return ResponseEntity.ok(myPageService.getSummary());
    }
}
