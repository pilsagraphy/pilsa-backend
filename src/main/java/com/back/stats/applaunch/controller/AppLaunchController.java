package com.back.stats.applaunch.controller;

import com.back.global.security.AuthUtils;
import com.back.stats.applaunch.mapper.StatsAppLaunchMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설치형 앱(TWA) 실행 신호 — 프론트가 앱으로 열린 세션에서 로그인 뒤 하루 한 번 부른다.
 *
 * 테스트 기간(Play 비공개 테스트)에는 "매일 앱 아이콘으로 접속" 이 참여 조건이라, 회원별로 그날 앱을 열었는지가 필요하다.
 * 접속 통계(stats_access_hourly)는 브라우저 접속과 구분이 안 되므로 앱 쪽 신호를 따로 받는다.
 * 관리자 화면: GET /api/admin/monitoring/app-launches.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "통계-앱 실행")
public class AppLaunchController {

    private final StatsAppLaunchMapper appLaunchMapper;

    @Operation(summary = "앱 실행 기록 (회원)",
            description = "설치형 앱(시작 URL /?launch=app)으로 연 세션에서 로그인 회원이 하루 한 번 부른다. "
                    + "회원·날짜당 1행(app_launch_daily)이며 같은 날 여러 번 불러도 횟수만 오른다. 응답 204.")
    @PostMapping("/api/user/app-launch")
    public ResponseEntity<Void> record() {
        Long userId = AuthUtils.currentUserId();
        try {
            appLaunchMapper.recordLaunch(userId);
        } catch (Exception e) {
            // 통계 기록이 본 기능을 막으면 안 된다 — 실패는 로그만
            log.warn("앱 실행 기록 실패 - userId={}, 원인={}", userId, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }
}
