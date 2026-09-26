package com.back.admin.monitoring.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 앱 접속 점검 — 회원 1명의 그날 상태 */
@Getter
@Setter
public class AppLaunchMemberResponse {
    private Long userId;
    private String name;
    private String memberType;
    private Integer adminLevel;
    private LocalDate joinedDate;
    private Boolean launched;            // 그날 앱을 열었는가
    private LocalDateTime launchedAt;    // 그날 처음 앱을 연 시각
    private Integer launchCount;
    private LocalDateTime webAccessedAt; // 그날 첫 인증 요청(브라우저·앱 구분 없음). 앱은 안 열었지만 웹으로는 들어온 사람 구분용
    private LocalDate lastLaunchDate;    // 그날 이전까지 마지막으로 앱을 연 날 (없으면 null)
    private Integer missStreak;          // 그날까지 연속으로 앱을 안 연 일수 (그날 열었으면 0). 가입일·기록 시작일 이전은 세지 않는다
}
