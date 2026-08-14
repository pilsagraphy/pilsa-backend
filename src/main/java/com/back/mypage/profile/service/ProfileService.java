package com.back.mypage.profile.service;

import com.back.mypage.profile.dto.ProfileResponse;

public interface ProfileService {
  ProfileResponse getCurrentUserRole();
}