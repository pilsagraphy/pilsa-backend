package com.back.mypage.profile.service;

import com.back.global.security.AuthUtils;
import com.back.mypage.profile.dto.MyPageBasicInfoRow;
import com.back.mypage.profile.dto.MyPageSummaryResponse;
import com.back.mypage.profile.dto.SemesterActivityResponse;
import com.back.mypage.profile.mapper.MyPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageServiceImpl implements MyPageService {

    // 학기 기준월이 policy_settings 에 없거나 잘못된 값일 때의 기본값 (1학기=3월, 2학기=9월 시작)
    private static final int DEFAULT_SEMESTER1_START_MONTH = 3;
    private static final int DEFAULT_SEMESTER2_START_MONTH = 9;

    private final MyPageMapper myPageMapper;

    @Override
    /** 이름·연락처 고치기 — 회원가입과 같은 규칙(이름 2~20자, 연락처 010-0000-0000). 연락처가 다른 회원 것이면 409 */
    @org.springframework.transaction.annotation.Transactional
    public void updateProfile(String name, String phone) {
        Long userId = com.back.global.security.AuthUtils.currentUserId();
        String n = name == null ? "" : name.strip();
        String p = phone == null ? "" : phone.strip();
        if (n.length() < 2 || n.length() > 20) throw new com.back.global.exception.BaseException("이름은 2~20자로 적어 주세요.", org.springframework.http.HttpStatus.BAD_REQUEST);
        if (!p.matches("^010-[0-9]{4}-[0-9]{4}$")) throw new com.back.global.exception.BaseException("연락처는 010-0000-0000 꼴로 적어 주세요.", org.springframework.http.HttpStatus.BAD_REQUEST);
        if (myPageMapper.existsPhoneOfOther(p, userId)) throw new com.back.global.exception.BaseException("이미 다른 회원이 쓰는 연락처예요.", org.springframework.http.HttpStatus.CONFLICT);
        if (myPageMapper.updateProfile(userId, n, p) == 0) throw new com.back.global.exception.BaseException("회원 정보를 찾지 못했어요.", org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Override
    public MyPageSummaryResponse getSummary() {
        Long userId = AuthUtils.currentUserId();

        MyPageBasicInfoRow basicInfo = myPageMapper.findBasicInfo(userId);

        int postCount = myPageMapper.countMyPosts(userId);
        int commentCount = myPageMapper.countMyComments(userId);
        int likedCount = myPageMapper.countMyLikedPosts(userId);

        LocalDateTime[] semesterRange = resolveCurrentSemesterRange();
        LocalDateTime from = semesterRange[0];
        LocalDateTime to = semesterRange[1];

        SemesterActivityResponse semester = new SemesterActivityResponse(
                myPageMapper.countMyPostsInPeriod(userId, from, to),
                myPageMapper.countMyCommentsInPeriod(userId, from, to),
                myPageMapper.countMyReceivedLikesInPeriod(userId, from, to)
        );

        return new MyPageSummaryResponse(basicInfo.getLoginId(), basicInfo.getName(), basicInfo.getPhone(), basicInfo.getJoinedAt(),
                postCount, commentCount, likedCount, semester);
    }

    // 오늘 기준 현재 학기의 [시작, 끝) 구간. 2학기는 연말을 넘기므로(예: 9월~다음해 2월) 연도 보정이 필요하다
    private LocalDateTime[] resolveCurrentSemesterRange() {
        int semester1StartMonth = parseMonth(
                myPageMapper.findPolicySetting("semester1_start_month"), DEFAULT_SEMESTER1_START_MONTH);
        int semester2StartMonth = parseMonth(
                myPageMapper.findPolicySetting("semester2_start_month"), DEFAULT_SEMESTER2_START_MONTH);

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        LocalDate start;
        LocalDate endExclusive;
        if (month >= semester1StartMonth && month < semester2StartMonth) {
            start = LocalDate.of(year, semester1StartMonth, 1);
            endExclusive = LocalDate.of(year, semester2StartMonth, 1);
        } else if (month >= semester2StartMonth) {
            start = LocalDate.of(year, semester2StartMonth, 1);
            endExclusive = LocalDate.of(year + 1, semester1StartMonth, 1);
        } else {
            start = LocalDate.of(year - 1, semester2StartMonth, 1);
            endExclusive = LocalDate.of(year, semester1StartMonth, 1);
        }
        return new LocalDateTime[]{start.atStartOfDay(), endExclusive.atStartOfDay()};
    }

    private int parseMonth(String value, int defaultValue) {
        try {
            int month = Integer.parseInt(value);
            return (month >= 1 && month <= 12) ? month : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
