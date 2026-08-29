package com.back.auth.local.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindIdVerifyRequest {
    private String email;
    private String code;
}