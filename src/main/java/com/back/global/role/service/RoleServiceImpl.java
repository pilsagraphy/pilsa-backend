package com.back.global.role.service;

import com.back.global.role.dto.RoleResponse;
import com.back.global.role.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.back.global.security.AuthUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {
  
  private final RoleMapper roleMapper;
  
  @Override
  public RoleResponse getCurrentUserRole() {
    Long userId = AuthUtils.currentUserId();
    
    RoleResponse info = roleMapper.findMemberInfoByUserId(userId);
    if (info == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
    }

    return info;
  }
}