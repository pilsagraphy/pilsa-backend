package com.back.global.role.service;

import com.back.global.role.dto.RoleResponse;

public interface RoleService {
  RoleResponse getCurrentUserRole();
}