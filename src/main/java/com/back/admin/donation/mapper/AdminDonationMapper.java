package com.back.admin.donation.mapper;

import com.back.admin.donation.dto.DonationAdminDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 명예의 전당 관리 (donations) — 소프트삭제(state) */
@Mapper
public interface AdminDonationMapper {

    List<DonationAdminDto> findAll();

    DonationAdminDto findById(@Param("donationId") Long donationId);

    boolean existsUser(@Param("userId") Long userId, @Param("activeOnly") boolean activeOnly);

    void insert(@Param("d") DonationAdminDto donation, @Param("adminId") Long adminId);

    int update(@Param("donationId") Long donationId, @Param("d") DonationAdminDto donation);

    int updatePhoto(@Param("donationId") Long donationId, @Param("photoUrl") String photoUrl);

    int softDelete(@Param("donationId") Long donationId);
}
