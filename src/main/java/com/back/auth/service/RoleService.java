package com.back.auth.service;

import com.back.auth.dto.RoleResponse;
import com.back.auth.dto.UserNameResponse;

public interface RoleService {
  RoleResponse getCurrentUserRole();

  UserNameResponse getCurrentUserName();
}