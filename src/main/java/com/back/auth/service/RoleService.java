package com.back.auth.service;

import com.back.auth.dto.RoleResponse;

public interface RoleService {
  RoleResponse getCurrentUserRole();
}