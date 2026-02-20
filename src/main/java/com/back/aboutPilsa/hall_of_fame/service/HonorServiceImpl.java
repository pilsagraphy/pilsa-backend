package com.back.aboutPilsa.hall_of_fame.service;

import com.back.aboutPilsa.hall_of_fame.dto.HonorResponse;
import com.back.aboutPilsa.hall_of_fame.mapper.HonorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HonorServiceImpl implements HonorService {

    private final HonorMapper honorMapper;

    @Override
    public List<HonorResponse> getHonorList() {
        List<HonorResponse> honors = honorMapper.selectHonorList();

        // 익명 처리 : isAnonymous가 true면 이름을 '익명후원자'로 변경
        return honors.stream().map(honor -> {
            if (honor.isAnonymous()) {
                honor.setDisplayName("익명후원자");
                honor.setAffiliation(null); // 소속도 숨김 처리 일단 함 할까요 말까요
            }
            return honor;
        }).collect(Collectors.toList());
    }
}