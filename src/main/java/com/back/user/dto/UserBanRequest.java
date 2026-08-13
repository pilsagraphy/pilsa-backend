package com.back.user.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

// 회원 영구차단(permanent) 요청 - 단일 또는 다중 회원
@Getter
@Setter
@ToString
public class UserBanRequest {
    private List<Long> userIds;
}
