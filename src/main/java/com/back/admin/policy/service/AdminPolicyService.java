package com.back.admin.policy.service;

import com.back.admin.policy.dto.AdminPolicyResponse;
import com.back.admin.policy.dto.BanPolicyItem;
import com.back.admin.policy.dto.PolicySettingItem;
import com.back.admin.policy.dto.UpdateBanPolicyRequest;
import com.back.admin.policy.dto.UpdateSettingRequest;
import com.back.admin.policy.mapper.AdminPolicyMapper;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 운영 관리 > 정책 설정 · 알림 설정.
 *
 * policy_settings 는 코드가 실행 중에 읽는 값이라(제재 수치 · 가입 형식 · 통계 기준 · 알림 스위치) 잘못 넣으면 바로 동작이 바뀐다.
 * 그래서 조회는 관리자 누구나, <b>수정은 admin_level 3</b> 만 하게 한다. 값 형식은 키 이름으로 최소한만 검사한다
 * (숫자 키는 숫자, regex 키는 컴파일 가능, notify_ 키는 0/1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPolicyService {

    /** 수정 권한 — 되돌리기 어려운 값(가입 형식 · 제재 기준)이 섞여 있어 최고 레벨만 */
    private static final int EDIT_LEVEL = 3;

    private static final Set<String> BAN_TYPES = Set.of("temporary", "permanent");

    private final AdminPolicyMapper mapper;

    public AdminPolicyResponse getAll() {
        AuthUtils.requireAdmin();
        AdminPolicyResponse res = new AdminPolicyResponse();
        res.setSettings(mapper.findAllSettings());
        res.setBanPolicies(mapper.findBanPolicies());
        return res;
    }

    @Transactional
    public PolicySettingItem updateSetting(String code, UpdateSettingRequest request) {
        AuthUtils.requireAdminLevel(EDIT_LEVEL);
        PolicySettingItem current = mapper.findSettingByCode(code);
        if (current == null) {
            throw new BaseException("없는 설정 키입니다: " + code, HttpStatus.NOT_FOUND);
        }
        String value = request == null || request.getSettingValue() == null ? null : request.getSettingValue().trim();
        if (value == null || value.isEmpty()) {
            throw new BaseException("설정 값은 비울 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (value.length() > 100) {
            throw new BaseException("설정 값은 100자 이하여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        validateByCode(code, value);

        String description = request.getDescription();
        if (description != null) {
            description = description.trim();
            if (description.length() > 200) {
                throw new BaseException("설명은 200자 이하여야 합니다.", HttpStatus.BAD_REQUEST);
            }
        }
        mapper.updateSetting(code, value, description);
        log.info("정책 설정 변경 - code={}, {} → {}, by={}", code, current.getSettingValue(), value, AuthUtils.currentUserId());
        return mapper.findSettingByCode(code);
    }

    /** 키 이름으로 형식을 고른다 — 새 키가 생겨도 이름 규칙만 지키면 검사가 따라간다 */
    static void validateByCode(String code, String value) {
        if (code.startsWith("notify_")) {
            if (!value.equals("0") && !value.equals("1")) {
                throw new BaseException("알림 스위치는 1(보냄) 또는 0(안 보냄)이어야 합니다.", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (code.endsWith("_regex")) {
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                throw new BaseException("정규식이 올바르지 않습니다: " + e.getDescription(), HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (code.endsWith("_extensions")) {
            if (!value.matches("[a-z0-9]+(,[a-z0-9]+)*")) {
                throw new BaseException("확장자 목록은 소문자·숫자를 쉼표로 이어야 합니다 (예: jpg,png).", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        // 그 밖의 키는 전부 수치(일수 · 개수 · 배수 · 가중치)
        if (!value.matches("-?[0-9]+(\\.[0-9]+)?")) {
            throw new BaseException("이 설정은 숫자여야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public BanPolicyItem updateBanPolicy(int warningNo, UpdateBanPolicyRequest request) {
        AuthUtils.requireAdminLevel(EDIT_LEVEL);
        if (request == null || request.getBanType() == null || !BAN_TYPES.contains(request.getBanType())) {
            throw new BaseException("banType 은 temporary 또는 permanent 여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        Integer days = null;
        if (request.getBanType().equals("temporary")) {
            if (request.getBanDays() == null || request.getBanDays() < 1) {
                throw new BaseException("temporary 는 정지 일수(1 이상)가 필요합니다.", HttpStatus.BAD_REQUEST);
            }
            days = request.getBanDays();
        }
        String description = request.getDescription() == null ? null : request.getDescription().trim();
        if (description != null && description.length() > 200) {
            throw new BaseException("설명은 200자 이하여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        int updated = mapper.updateBanPolicy(warningNo, request.getBanType(), days, description);
        if (updated == 0) {
            throw new BaseException("없는 경고 단계입니다: " + warningNo, HttpStatus.NOT_FOUND);
        }
        log.info("정지 단계 변경 - warningNo={}, type={}, days={}, by={}", warningNo, request.getBanType(), days,
                AuthUtils.currentUserId());
        return mapper.findBanPolicies().stream()
                .filter(b -> b.getWarningNo() != null && b.getWarningNo() == warningNo)
                .findFirst().orElse(null);
    }
}
