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

        // 전화번호: 회원가입(signup)과 동일하게 형식 제한 없이 원본 그대로 저장.
        // signup은 phone에 검증/정규화가 전혀 없어 010-1234-5678 같은 실제 번호가 이미 저장돼 있으므로,
        // 여기서 자릿수/형식을 강제하면 그런 회원의 phone을 admin이 재저장할 수 없게 됨.
        // 단, 중복은 저장 형식 차이(하이픈 유무)와 무관하게 숫자 기준으로 미리 잡아 DB unique 충돌(500) 방지.
        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            String digits = phone.replaceAll("[^0-9]", "");
            if (!digits.isEmpty() && memberMapper.existsPhoneDigitsExcludingUser(digits, userId)) {
                throw new MemberException("이미 사용 중인 전화번호입니다.", HttpStatus.CONFLICT);
            }
            request.setPhone(phone);
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
        // 이미 영구차단된 회원을 임시정지로 덮어쓰면 만료 시 영구차단이 사라지므로 차단
        if (BAN_TYPE_PERMANENT.equals(memberMapper.findBanStatus(userId))) {
            throw new MemberException("이미 영구차단된 회원입니다. 정지로 변경할 수 없습니다.", HttpStatus.CONFLICT);
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

        // 존재 확인 1회 (WHERE user_id IN ...). 없는 회원이 섞이면 전체 실패
        List<Long> found = memberMapper.findActiveMemberIds(targetIds);
        if (found.size() != targetIds.size()) {
            List<Long> missing = targetIds.stream().filter(id -> !found.contains(id)).toList();
            throw new MemberException("존재하지 않는 회원이 포함되어 있습니다: " + missing, HttpStatus.NOT_FOUND);
        }

        // 영구차단: ends_at/banned_until = null. INSERT 1회(batch) + UPDATE 1회로 처리
        LocalDateTime startsAt = LocalDateTime.now();
        memberMapper.insertBanLogBatch(targetIds, WARNING_NO_PERMANENT, BAN_TYPE_PERMANENT, startsAt, null);
        memberMapper.updateUserBanStatusByIds(targetIds, BAN_TYPE_PERMANENT, null);

        log.info("회원 영구차단 완료 - {}명, ids: {}", targetIds.size(), targetIds);
        return new MemberResponse(targetIds.size() + "명을 영구차단했습니다.", null);
    }
}
