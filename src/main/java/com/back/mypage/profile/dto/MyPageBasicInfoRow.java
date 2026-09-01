package com.back.mypage.profile.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// users 조회 결과 (마이페이지 상단 기본 정보)
@Getter
@Setter
public class MyPageBasicInfoRow {
    private String loginId;
    private String name;
    private LocalDateTime joinedAt;
}
