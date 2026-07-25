package com.back.auth.dto;

import lombok.Data;

@Data
public class UserDto {
    private Long userId;
    private String loginId;
    private String password;
    private String passwordHash;
    private String name;
    private String email;
    private String role;
    private Boolean isDeleted;
}
