package com.back.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {
  private String memberType;   // STUDENT / ALUMNI
  private Integer adminLevel;   // 0=일반, 1~3=관리자
}