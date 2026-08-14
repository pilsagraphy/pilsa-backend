package com.back.donation.service;

import com.back.donation.dto.DonationResponse;
import com.back.donation.mapper.DonationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DonationServiceImpl implements DonationService {

    private final DonationMapper DonationMapper;

    @Override
    public List<DonationResponse> getDonationList() {
        List<DonationResponse> Donations = DonationMapper.selectDonationList();

        // 익명 처리 : isAnonymous가 true면 이름을 '익명후원자'로 변경
        return Donations.stream().map(Donation -> {
            if (Boolean.TRUE.equals(Donation.getIsAnonymous())) {
                Donation.setDisplayName("익명후원자");
                Donation.setAffiliation(null); // 소속도 숨김 처리
                Donation.setPhotoUrl(null); // 익명일 경우 사진 경로도 제거
            }
            return Donation;
        }).collect(Collectors.toList());
    }
}