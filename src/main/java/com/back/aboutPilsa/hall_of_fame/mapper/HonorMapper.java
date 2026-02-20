package com.back.aboutPilsa.hall_of_fame.mapper;

import com.back.aboutPilsa.hall_of_fame.dto.HonorResponse;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface HonorMapper {
    // 명예의 전당 목록 조회
    List<HonorResponse> selectHonorList();
    // 금액 높은순으로 정렬해서 가져오고, 같은 금액있으면 최신으로 정렬
}