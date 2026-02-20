package com.back.aboutPilsa.hall_of_fame.service;

import com.back.aboutPilsa.hall_of_fame.dto.HonorResponse;
import java.util.List;

public interface HonorService {
    List<HonorResponse> getHonorList();
}