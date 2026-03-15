package com.back.global.role.controller;

import com.back.global.role.dto.RoleResponse;
import com.back.global.role.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoleController {
  
  private final RoleService roleService;
  
  @GetMapping("/role")
  public RoleResponse getMyRole() {
    return roleService.getCurrentUserRole();
  }
}