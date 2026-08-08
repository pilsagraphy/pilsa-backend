package com.back.member.service;

import com.back.member.dto.MemberBanRequest;
import com.back.member.dto.MemberListResponse;
import com.back.member.dto.MemberPageResponse;
import com.back.member.dto.MemberResponse;
import com.back.member.dto.MemberSuspendRequest;
import com.back.member.dto.MemberUpdateRequest;
import com.back.member.exception.MemberException;
import com.back.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;

    // ===== 회원 정보 수정 검증 규칙 =====
    // 이름: 한글/영문 문자만 (숫자·특수문자 불가), 공백 허용
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z\\s]+$");
    // 전화번호: 숫자 8자리 (하이픈 제거 후 검증)
    private static final Pattern PHONE_DIGITS_PATTERN = Pattern.compile("^\\d{8}$");
    // 학번: 숫자 10자리
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("^\\d{10}$");
    // 이메일 형식
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    // 재학상태: 0(재학) / 1(휴학) / 2(졸업)
    private static final Set<Integer> ALLOWED_STATUS = Set.of(0, 1, 2);
    // 권한: DB에 저장되는 실제 role 값 (관리 Lv 체계는 추후 확장)
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "STUDENTS", "ALUMNI");

    // ===== 정지/차단 상수 =====
    // ban_log.warning_no는 ban_policy FK 통과용. 수동 차단은 기존 정책값 재사용
    // (임시=1/BAN_W1, 영구=3/BAN_W3). 실제 유형·기간은 ban_type·ends_at가 담당.
    private static final int WARNING_NO_TEMPORARY = 1;
    private static final int WARNING_NO_PERMANENT = 3;
    private static final String BAN_TYPE_TEMPORARY = "temporary";
    private static final String BAN_TYPE_PERMANENT = "permanent";

    // 관리자 권한 확인 (경로는 SecurityConfig에서 이미 ADMIN 제한, 방어적으로 한 번 더 확인)
    private void checkAdminRole() {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new MemberException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public MemberPageResponse getMembers(int page, int size, String keyword, String sort) {
        checkAdminRole();

        if (page < 1) {
            throw new MemberException("페이지 번호는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (size < 1) {
            throw new MemberException("페이지 크기는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }

        long totalCount = memberMapper.countMembers(keyword);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        // 검색 결과가 없으면(회원 0명) 빈 목록을 그대로 반환 - 관리자 화면이므로 에러로 막지 않음
        List<MemberListResponse> members = totalCount == 0
                ? List.of()
                : memberMapper.findMembers(keyword, sort, (page - 1) * size, size);

        MemberPageResponse response = new MemberPageResponse();
        response.setTotalPages(totalPages);
        response.setTotalCount(totalCount);
        response.setMembers(members);
        return response;
    }

    @Override
    @Transactional
    public MemberResponse updateMember(Long userId, MemberUpdateRequest request) {
        checkAdminRole();

        // 수정 대상(활성 회원) 존재 확인
        if (!memberMapper.existsActiveMember(userId)) {
            throw new MemberException("해당 회원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        // 전달된 필드만 검증 (부분 수정). 아무 필드도 없으면 잘못된 요청
        boolean hasAny = request.getName() != null || request.getPhone() != null
                || request.getStudentNo() != null || request.getEmail() != null
                || request.getStatus() != null || request.getRole() != null;
        if (!hasAny) {
            throw new MemberException("수정할 항목이 없습니다.", HttpStatus.BAD_REQUEST);
        }

        // 이름: 문자만
        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty() || !NAME_PATTERN.matcher(name).matches()) {
                throw new MemberException("이름은 문자만 입력할 수 있습니다.", HttpStatus.BAD_REQUEST);
            }
            request.setName(name);
        }

        // 전화번호: 8자리(4-4) 숫자만 → "0000-0000" 형식으로 저장
        if (request.getPhone() != null) {
            String digits = request.getPhone().replaceAll("[^0-9]", "");
            if (!PHONE_DIGITS_PATTERN.matcher(digits).matches()) {
                throw new MemberException("전화번호는 숫자 8자리여야 합니다.", HttpStatus.BAD_REQUEST);
            }
            String formatted = digits.substring(0, 4) + "-" + digits.substring(4);
            if (memberMapper.existsPhoneExcludingUser(formatted, userId)) {
                throw new MemberException("이미 사용 중인 전화번호입니다.", HttpStatus.CONFLICT);
            }
            request.setPhone(formatted);
        }

        // 학번: 10자리 숫자만
        if (request.getStudentNo() != null) {
            String studentNo = request.getStudentNo().trim();
            if (!STUDENT_NO_PATTERN.matcher(studentNo).matches()) {
                throw new MemberException("학번은 숫자 10자리여야 합니다.", HttpStatus.BAD_REQUEST);
            }
            if (memberMapper.existsStudentNoExcludingUser(studentNo, userId)) {
                throw new MemberException("이미 사용 중인 학번입니다.", HttpStatus.CONFLICT);
            }
            request.setStudentNo(studentNo);
        }

        // 이메일: 형식 검증 + 중복 확인
        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new MemberException("올바른 이메일 형식이 아닙니다.", HttpStatus.BAD_REQUEST);
            }
            if (memberMapper.existsEmailExcludingUser(email, userId)) {
                throw new MemberException("이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT);
            }
            request.setEmail(email);
        }

        // 재학상태: 0/1/2
        if (request.getStatus() != null && !ALLOWED_STATUS.contains(request.getStatus())) {
            throw new MemberException("유효하지 않은 재학상태 값입니다.", HttpStatus.BAD_REQUEST);
        }

        // 권한: 허용된 role 값
        if (request.getRole() != null && !ALLOWED_ROLES.contains(request.getRole())) {
            throw new MemberException("유효하지 않은 권한 값입니다.", HttpStatus.BAD_REQUEST);
        }

        memberMapper.updateMember(userId, request);
        log.info("회원 정보 수정 완료 - userId: {}, 요청: {}", userId, request);
        return new MemberResponse("회원 정보가 수정되었습니다.", userId);
    }

    @Override
    @Transactional
    public MemberResponse suspendMember(Long userId, MemberSuspendRequest request) {
        checkAdminRole();

        if (!memberMapper.existsActiveMember(userId)) {
            throw new MemberException("해당 회원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        if (request.getEndDate() == null) {
            throw new MemberException("정지 종료일은 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        // 시작 = 지금, 종료 = 종료일의 하루 끝(23:59:59). 종료는 항상 시작보다 미래여야 함
        LocalDateTime startsAt = LocalDateTime.now();
        LocalDateTime endsAt = request.getEndDate().atTime(23, 59, 59);
        if (!endsAt.isAfter(startsAt)) {
            throw new MemberException("정지 종료일은 현재 시점보다 미래여야 합니다.", HttpStatus.BAD_REQUEST);
        }

        memberMapper.insertBanLog(userId, WARNING_NO_TEMPORARY, BAN_TYPE_TEMPORARY, startsAt, endsAt);
        memberMapper.updateUserBanStatus(userId, BAN_TYPE_TEMPORARY, endsAt);

        log.info("회원 정지 완료 - userId: {}, 종료일: {}", userId, request.getEndDate());
        return new MemberResponse("회원이 정지되었습니다. (종료일: " + request.getEndDate() + ")", userId);
    }

    @Override
    @Transactional
    public MemberResponse banMembers(MemberBanRequest request) {
        checkAdminRole();

        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new MemberException("차단할 회원을 선택해야 합니다.", HttpStatus.BAD_REQUEST);
        }

        // null 제거 + 중복 제거
        List<Long> targetIds = request.getUserIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (targetIds.isEmpty()) {
            throw new MemberException("차단할 회원을 선택해야 합니다.", HttpStatus.BAD_REQUEST);
        }

        // 존재하지 않는 회원이 섞여 있으면 전체 실패 (트랜잭션 all-or-nothing)
        List<Long> missing = targetIds.stream()
                .filter(id -> !memberMapper.existsActiveMember(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new MemberException("존재하지 않는 회원이 포함되어 있습니다: " + missing, HttpStatus.NOT_FOUND);
        }

        LocalDateTime startsAt = LocalDateTime.now();
        for (Long id : targetIds) {
            // 영구차단: ends_at/banned_until = null
            memberMapper.insertBanLog(id, WARNING_NO_PERMANENT, BAN_TYPE_PERMANENT, startsAt, null);
            memberMapper.updateUserBanStatus(id, BAN_TYPE_PERMANENT, null);
        }

        log.info("회원 영구차단 완료 - {}명, ids: {}", targetIds.size(), targetIds);
        return new MemberResponse(targetIds.size() + "명을 영구차단했습니다.", null);
    }
}
