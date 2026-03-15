package com.back.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindIdVerifyRequest {
    private String email;
    private String code;
}