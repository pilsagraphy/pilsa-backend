package com.back.member.service;

import com.back.member.dto.MemberListResponse;
import com.back.member.dto.MemberPageResponse;
import com.back.member.exception.MemberException;
import com.back.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;

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
}
