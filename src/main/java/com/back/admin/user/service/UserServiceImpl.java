package com.back.admin.user.service;

import com.back.admin.user.dto.UserBanRequest;
import com.back.admin.user.dto.UserListResponse;
import com.back.admin.user.dto.UserPageResponse;
import com.back.admin.user.dto.UserResponse;
import com.back.admin.user.dto.UserSuspendRequest;
import com.back.admin.user.dto.UserUpdateRequest;
import com.back.admin.user.exception.UserException;
import com.back.admin.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import com.back.global.security.AuthUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    // ===== 회원 정보 수정 검증 규칙 =====
    // 이름: 한글/영문 문자만 (숫자·특수문자 불가), 공백 허용
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z\\s]+$");
    // 학번: 숫자 10자리
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("^\\d{10}$");
    // 이메일 형식
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    // 회원 구분: PR #66 권한 개편 이후 users.member_type 실제 값
    private static final Set<String> ALLOWED_MEMBER_TYPES = Set.of("STUDENT", "ALUMNI");
    // 관리 권한 레벨: 0(일반) / 1~3(관리자). 화면의 "관리 Lv.1~3" 라벨과 대응
    private static final int MIN_ADMIN_LEVEL = 0;
    private static final int MAX_ADMIN_LEVEL = 3;

    // ===== 정지/차단 상수 =====
    // 관리자 수동 조치는 경고 누적과 무관하므로 ban_log.warning_no = NULL, source = 'manual' 로 기록한다.
    // (예전에는 FK 통과용으로 warning_no 1/3을 빌려 써서 "경고 1회"로 잘못 집계됐다)
    private static final String BAN_TYPE_TEMPORARY = "temporary";
    private static final String BAN_TYPE_PERMANENT = "permanent";

    // 관리자 권한 확인 (URL 규칙과 별개로 서비스에서 한 번 더 확인 - AuthUtils 공통 유틸 사용)
    private void checkAdminRole() {
        if (!AuthUtils.isAdmin()) {
            throw new UserException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public UserPageResponse getUsers(int page, int size, String keyword, String sort) {
        checkAdminRole();

        if (page < 1) {
            throw new UserException("페이지 번호는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (size < 1) {
            throw new UserException("페이지 크기는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }

        long totalCount = userMapper.countUsers(keyword);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        // 검색 결과가 없으면(회원 0명) 빈 목록을 그대로 반환 - 관리자 화면이므로 에러로 막지 않음
        List<UserListResponse> users = totalCount == 0
                ? List.of()
                : userMapper.findUsers(keyword, sort, (page - 1) * size, size);

        UserPageResponse response = new UserPageResponse();
        response.setTotalPages(totalPages);
        response.setTotalCount(totalCount);
        response.setMembers(users);
        return response;
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        checkAdminRole();

        // 수정 대상(활성 회원) 존재 확인
        if (!userMapper.existsActiveUser(userId)) {
            throw new UserException("해당 회원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        // 전달된 필드만 검증 (부분 수정). 아무 필드도 없으면 잘못된 요청
        boolean hasAny = request.getName() != null || request.getPhone() != null
                || request.getStudentNo() != null || request.getEmail() != null
                || request.getMemberType() != null || request.getAdminLevel() != null;
        if (!hasAny) {
            throw new UserException("수정할 항목이 없습니다.", HttpStatus.BAD_REQUEST);
        }

        // 이름: 문자만
        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty() || !NAME_PATTERN.matcher(name).matches()) {
                throw new UserException("이름은 문자만 입력할 수 있습니다.", HttpStatus.BAD_REQUEST);
            }
            request.setName(name);
        }

        // 전화번호: 회원가입(signup)과 동일하게 형식 제한 없이 원본 그대로 저장.
        // signup은 phone에 검증/정규화가 전혀 없어 010-1234-5678 같은 실제 번호가 이미 저장돼 있으므로,
        // 여기서 자릿수/형식을 강제하면 그런 회원의 phone을 admin이 재저장할 수 없게 됨.
        // 단, 중복은 저장 형식 차이(하이픈 유무)와 무관하게 숫자 기준으로 미리 잡아 DB unique 충돌(500) 방지.
        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            String digits = phone.replaceAll("[^0-9]", "");
            if (!digits.isEmpty() && userMapper.existsPhoneDigitsExcludingUser(digits, userId)) {
                throw new UserException("이미 사용 중인 전화번호입니다.", HttpStatus.CONFLICT);
            }
            request.setPhone(phone);
        }

        // 학번: 10자리 숫자만
        if (request.getStudentNo() != null) {
            String studentNo = request.getStudentNo().trim();
            if (!STUDENT_NO_PATTERN.matcher(studentNo).matches()) {
                throw new UserException("학번은 숫자 10자리여야 합니다.", HttpStatus.BAD_REQUEST);
            }
            if (userMapper.existsStudentNoExcludingUser(studentNo, userId)) {
                throw new UserException("이미 사용 중인 학번입니다.", HttpStatus.CONFLICT);
            }
            request.setStudentNo(studentNo);
        }

        // 이메일: 형식 검증 + 중복 확인
        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new UserException("올바른 이메일 형식이 아닙니다.", HttpStatus.BAD_REQUEST);
            }
            if (userMapper.existsEmailExcludingUser(email, userId)) {
                throw new UserException("이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT);
            }
            request.setEmail(email);
        }

        // 회원 구분: STUDENT / ALUMNI
        if (request.getMemberType() != null && !ALLOWED_MEMBER_TYPES.contains(request.getMemberType())) {
            throw new UserException("유효하지 않은 회원 구분 값입니다. (STUDENT/ALUMNI)", HttpStatus.BAD_REQUEST);
        }

        // 관리 권한 레벨: 0~3
        if (request.getAdminLevel() != null
                && (request.getAdminLevel() < MIN_ADMIN_LEVEL || request.getAdminLevel() > MAX_ADMIN_LEVEL)) {
            throw new UserException("유효하지 않은 관리 권한 레벨입니다. (0~3)", HttpStatus.BAD_REQUEST);
        }

        userMapper.updateUser(userId, request);
        log.info("회원 정보 수정 완료 - userId: {}, 요청: {}", userId, request);
        return new UserResponse("회원 정보가 수정되었습니다.", userId);
    }

    @Override
    @Transactional
    public UserResponse suspendUser(Long userId, UserSuspendRequest request) {
        checkAdminRole();

        if (!userMapper.existsActiveUser(userId)) {
            throw new UserException("해당 회원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        // 이미 영구차단된 회원을 임시정지로 덮어쓰면 만료 시 영구차단이 사라지므로 차단
        if (BAN_TYPE_PERMANENT.equals(userMapper.findBanStatus(userId))) {
            throw new UserException("이미 영구차단된 회원입니다. 정지로 변경할 수 없습니다.", HttpStatus.CONFLICT);
        }
        if (request.getEndDate() == null) {
            throw new UserException("정지 종료일은 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        // 시작 = 지금, 종료 = 종료일의 하루 끝(23:59:59). 종료는 항상 시작보다 미래여야 함
        LocalDateTime startsAt = LocalDateTime.now();
        LocalDateTime endsAt = request.getEndDate().atTime(23, 59, 59);
        if (!endsAt.isAfter(startsAt)) {
            throw new UserException("정지 종료일은 현재 시점보다 미래여야 합니다.", HttpStatus.BAD_REQUEST);
        }

        userMapper.insertBanLog(userId, BAN_TYPE_TEMPORARY, startsAt, endsAt);
        userMapper.updateUserBanStatus(userId, BAN_TYPE_TEMPORARY, endsAt);

        log.info("회원 정지 완료 - userId: {}, 종료일: {}", userId, request.getEndDate());
        return new UserResponse("회원이 정지되었습니다. (종료일: " + request.getEndDate() + ")", userId);
    }

    @Override
    @Transactional
    public UserResponse banUsers(UserBanRequest request) {
        checkAdminRole();

        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new UserException("차단할 회원을 선택해야 합니다.", HttpStatus.BAD_REQUEST);
        }

        // null 제거 + 중복 제거
        List<Long> targetIds = request.getUserIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (targetIds.isEmpty()) {
            throw new UserException("차단할 회원을 선택해야 합니다.", HttpStatus.BAD_REQUEST);
        }

        // 존재 확인 1회 (WHERE user_id IN ...). 없는 회원이 섞이면 전체 실패
        List<Long> found = userMapper.findActiveUserIds(targetIds);
        if (found.size() != targetIds.size()) {
            List<Long> missing = targetIds.stream().filter(id -> !found.contains(id)).toList();
            throw new UserException("존재하지 않는 회원이 포함되어 있습니다: " + missing, HttpStatus.NOT_FOUND);
        }

        // 영구차단: ends_at/banned_until = null. INSERT 1회(batch) + UPDATE 1회로 처리
        LocalDateTime startsAt = LocalDateTime.now();
        userMapper.insertBanLogBatch(targetIds, BAN_TYPE_PERMANENT, startsAt, null);
        userMapper.updateUserBanStatusByIds(targetIds, BAN_TYPE_PERMANENT, null);

        log.info("회원 영구차단 완료 - {}명, ids: {}", targetIds.size(), targetIds);
        return new UserResponse(targetIds.size() + "명을 영구차단했습니다.", null);
    }
}
