package com.back.member.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 회원 목록 - 행 단위 응답 DTO
@Getter
@Setter
public class MemberListResponse {

    // [화면에 보여지지 않지만 꼭 가져와야 하는 것] 회원 선택/정지/차단 대상 식별용
    private Long userId;

    // [화면 표시] 검색 결과 컬럼
    private String loginId;      // ID (예: CH400)
    private String name;         // 이름
    private String phone;        // 전화번호
    private String studentNo;    // 학번
    private String email;        // Email
    private Integer status;      // 재학상태 (0: 재학 / 1: 휴학 / 2: 졸업)
    private String role;         // 권한 (ADMIN / STUDENTS / ALUMNI)
    private int postCount;       // 게시글 수
    private int commentCount;    // 댓글 수

    // 정지 기간 (현재 유효한 차단 1건 기준) - 차단 없으면 null → 화면에서 "-"
    private LocalDateTime banStartAt;  // 정지 시작 (ban_log.starts_at)
    private LocalDateTime banEndAt;    // 정지 종료 (ban_log.ends_at, 영구차단이면 null)

    // [화면에 보여지지 않지만 꼭 가져와야 하는 것] 차단 상태 캐시 (none / temporary / permanent)
    private String banStatus;
}
