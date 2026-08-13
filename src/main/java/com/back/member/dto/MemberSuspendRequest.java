package com.back.member.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

// 회원 정지(temporary) 요청 - 단일 회원
// endDate: 정지 완료일(ISO yyyy-MM-dd). 시작은 지금(now), 종료는 이 날짜의 하루 끝.
@Getter
@Setter
@ToString
public class MemberSuspendRequest {
    private LocalDate endDate;
}
