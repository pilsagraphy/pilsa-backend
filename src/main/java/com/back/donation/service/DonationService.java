package com.back.donation.service;

import com.back.donation.dto.DonationResponse;
import java.util.List;

public interface DonationService {
    List<DonationResponse> getDonationList();
}
