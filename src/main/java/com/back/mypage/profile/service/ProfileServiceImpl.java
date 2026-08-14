package com.back.mypage.profile.service;

import com.back.mypage.profile.dto.ProfileResponse;
import com.back.mypage.profile.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.back.global.security.AuthUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileServiceImpl implements ProfileService {
  
  private final ProfileMapper profileMapper;
  
  @Override
  public ProfileResponse getCurrentUserRole() {
    Long userId = AuthUtils.currentUserId();
    
    ProfileResponse info = profileMapper.findMemberInfoByUserId(userId);
    if (info == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
    }

    return info;
  }
}