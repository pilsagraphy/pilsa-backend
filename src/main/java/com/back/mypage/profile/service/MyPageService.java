package com.back.mypage.profile.service;

import com.back.mypage.profile.dto.MyPageSummaryResponse;

public interface MyPageService {
    void updateProfile(String name, String phone);
    MyPageSummaryResponse getSummary();
}
