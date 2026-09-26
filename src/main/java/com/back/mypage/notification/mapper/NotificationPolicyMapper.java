package com.back.mypage.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 알림 발행 스위치(policy_settings notify_*) 조회 */
@Mapper
public interface NotificationPolicyMapper {

    /** 해당 코드의 setting_value. 행이 없으면 null */
    String findSettingValue(@Param("code") String code);
}
