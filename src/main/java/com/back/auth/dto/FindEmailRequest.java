package com.back.auth.dto;

import lombok.Getter;
import lombok.Setter;

// 이메일 찾기 요청 - 학번 + 이름
@Getter
@Setter
public class FindEmailRequest {
    private String studentNo;
    private String name;
}
