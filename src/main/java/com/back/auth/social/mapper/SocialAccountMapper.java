package com.back.auth.social.mapper;

import com.back.auth.social.dto.UserSocialAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SocialAccountMapper {

    /** 소셜 로그인 진입점 — 이메일이 아니라 provider + providerUserId 로 찾는다. */
    UserSocialAccount findByProviderUser(@Param("provider") String provider,
                                         @Param("providerUserId") String providerUserId);

    UserSocialAccount findByUserAndProvider(@Param("userId") Long userId,
                                            @Param("provider") String provider);

    /** 마이페이지에서 "연결된 소셜 계정" 목록을 그릴 때 (provider 가 늘면 여러 건이 된다). */
    List<UserSocialAccount> findByUserId(@Param("userId") Long userId);

    /** 연결. 같은 회원이 같은 provider 를 다시 연결하면 계정만 갈아끼운다. */
    int upsertLink(UserSocialAccount account);

    int deleteByUserAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

    int deleteByUserId(@Param("userId") Long userId);
}
