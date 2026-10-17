package com.back.admin.sanction.mapper;

import com.back.admin.sanction.dto.ForceWithdrawLogResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 강제 탈퇴 이력 조회 — 기록은 WithdrawMapper.insertForceWithdrawLog 가 남긴다 */
@Mapper
public interface ForceWithdrawLogMapper {

    /** 최신순. keyword 는 가린 이름·가린 아이디·사유·회원 번호에서 찾는다 */
    List<ForceWithdrawLogResponse.Row> findPage(@Param("keyword") String keyword,
                                                @Param("offset") int offset, @Param("size") int size);

    long count(@Param("keyword") String keyword);
}
