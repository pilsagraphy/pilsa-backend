package com.back.mypage.activity.controller;

import com.back.global.security.AuthUtils;
import com.back.mypage.activity.mapper.MyActivityGridMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 마이페이지 '잔디' — 깃허브 기여 그래프처럼 날짜별 칸으로 내 활동(글+댓글)과 접속을 본다 (PM 2026-10-10).
 * 접속은 stats_access_hourly(시간대당 1행)를 날짜로 묶은 것, 활동은 posts·comments 의 created_at 을 날짜로 묶은 것.
 * 조회뿐이라 서비스 없이 매퍼를 바로 쓴다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-활동")
public class MyActivityGridController {

    private static final int MAX_DAYS = 371; // 53주

    private final MyActivityGridMapper mapper;

    @Operation(summary = "내 잔디 (날짜별 활동·접속)", description = """
            `?days=112` (기본 112 = 16주, 최대 371). 오늘 포함 최근 days 일.
            ```json
            { "days": 112,
              "activity": [{ "date": "2026-10-10", "count": 3 }],   // 그 날 쓴 글 + 댓글 수
              "access":   [{ "date": "2026-10-10", "count": 5 }] }  // 그 날 접속한 시간대 수
            ```
            값이 0 인 날은 빠진다 — 화면이 날짜 격자를 만들고 채운다.""")
    @GetMapping("/api/user/mypage/activity-grid")
    public ResponseEntity<Map<String, Object>> grid(@RequestParam(defaultValue = "112") int days) {
        Long userId = AuthUtils.currentUserId();
        int span = Math.max(7, Math.min(MAX_DAYS, days));
        return ResponseEntity.ok(Map.of(
                "days", span,
                "activity", mapper.findActivityDaily(userId, span),
                "access", mapper.findAccessDaily(userId, span)));
    }
}
