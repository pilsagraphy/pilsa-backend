package com.back.stats.policy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 통계 배치가 쓰는 정책 수치 조회 (policy_settings.code -> setting_value)
@Mapper
public interface StatsPolicyMapper {

    // 행이 없으면 null → 호출부가 코드 기본값을 쓴다
    String findPolicySetting(@Param("code") String code);
}
