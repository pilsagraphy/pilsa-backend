package com.back.auth.local.service;

import com.back.auth.local.dto.RoleResponse;
import com.back.auth.local.dto.UserNameResponse;

public interface RoleService {
  RoleResponse getCurrentUserRole();

  UserNameResponse getCurrentUserName();
}