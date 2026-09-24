package com.back.donation.mapper;

import com.back.donation.dto.DonationResponse;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DonationMapper {
    // 명예의 전당 목록 조회
    List<DonationResponse> selectDonationList();
    // 금액 높은순으로 정렬해서 가져오고, 같은 금액있으면 최신으로 정렬
}