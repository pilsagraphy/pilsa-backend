package com.back.admin.policy.mapper;

import com.back.admin.policy.dto.BanPolicyItem;
import com.back.admin.policy.dto.PolicySettingItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 운영 관리 > 정책 설정 · 알림 설정 — policy_settings / ban_policy 조회·수정 */
@Mapper
public interface AdminPolicyMapper {

    List<PolicySettingItem> findAllSettings();

    PolicySettingItem findSettingByCode(@Param("code") String code);

    /** description 이 null 이면 값만 바꾼다 */
    int updateSetting(@Param("code") String code, @Param("settingValue") String settingValue,
                      @Param("description") String description);

    List<BanPolicyItem> findBanPolicies();

    int updateBanPolicy(@Param("warningNo") int warningNo, @Param("banType") String banType,
                        @Param("banDays") Integer banDays, @Param("description") String description);
}
