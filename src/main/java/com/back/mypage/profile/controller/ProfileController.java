package com.back.mypage.profile.controller;

import com.back.mypage.profile.dto.ProfileResponse;
import com.back.mypage.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class ProfileController {
  
  private final ProfileService profileService;
  
  @GetMapping("/profile")
  public ProfileResponse getMyRole() {
    return profileService.getCurrentUserRole();
  }
}