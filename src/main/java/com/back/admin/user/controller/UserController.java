package com.back.admin.user.controller;

import com.back.admin.user.dto.UserBanRequest;
import com.back.admin.user.dto.UserPageResponse;
import com.back.admin.user.dto.UserResponse;
import com.back.admin.user.dto.UserSuspendRequest;
import com.back.admin.user.dto.UserUpdateRequest;
import com.back.admin.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-회원 관리",
        description = "회원목록 페이지. 회원 검색·조회와 회원 정보 수정, 정지(temporary)·영구차단(permanent) 처리를 담당한다. 정지/차단/수정은 모두 PATCH 메서드다.")
public class UserController {

    private final UserService userService;

    // 회원 전체 목록 조회 (검색, 정렬, 페이지네이션) - 관리자 전용
    @Operation(summary = "회원 목록 (관리자)",
            description = """
                    회원목록 페이지 진입·검색 시 호출한다. 검색/정렬/페이징과 함께 회원별 활동 수(게시글·댓글)와 정지 기간을 내려준다.

                    ### 요청 예시
                    ```
                    GET /api/admin/users?page=1&size=10&keyword=검색어&sort=latest
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "totalPages":3,"totalCount":25,
                      "members":[{"userId":80,"loginId":"hong","name":"홍길동","phone":"010-1234-5678",
                        "studentNo":"2026010101","email":"hong@pilsa.co.kr","memberType":"STUDENT",
                        "adminLevel":0,"postCount":5,"commentCount":12,
                        "banStartAt":null,"banEndAt":null,"banStatus":"none"}]
                    }
                    ```

                    memberType: STUDENT(재학생) | ALUMNI(졸업생). adminLevel: 0=일반, 1~3=관리자. banStatus: none | temporary | permanent.
                    """)
    @GetMapping("/api/admin/users")
    public ResponseEntity<UserPageResponse> getUsers(
            @Parameter(description = "페이지 번호 (1부터)", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "페이지당 회원 수", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(description = "이름/아이디 등 검색어", example = "홍길동")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "정렬 기준 (기본 latest)", example = "latest")
            @RequestParam(value = "sort", defaultValue = "latest") String sort) {
        log.info("회원 목록 조회 요청 - page: {}, size: {}, keyword: {}, sort: {}", page, size, keyword, sort);
        UserPageResponse response = userService.getUsers(page, size, keyword, sort);
        log.info("회원 목록 조회 성공 - 총 {}명, 현재 페이지 {}건", response.getTotalCount(), response.getMembers().size());
        return ResponseEntity.ok(response);
    }

    // 회원 정보 수정 (이름/전화/학번/이메일 + 재학상태/권한) - 관리자 전용
    @Operation(summary = "회원 정보 수정 (관리자, PATCH 부분 수정)",
            description = """
                    회원목록 페이지에서 회원 정보를 편집·저장할 때 호출한다. 전달한 필드만 수정되며(부분 수정), 이메일은 수정할 수 없다.

                    ### 요청 예시
                    ```json
                    {"name":"홍길동","phone":"010-1234-5678","studentNo":"2026010101","memberType":"ALUMNI","adminLevel":1}
                    ```
                    - 전달한 필드만 수정된다. email 은 수정 불가.
                    - memberType: STUDENT | ALUMNI, adminLevel: 0~3.

                    ### 응답 예시
                    ```json
                    {"message":"회원 정보가 수정되었습니다.","userId":80}
                    ```

                    실패: 400 {"message":"유효하지 않은 회원 구분 값입니다. (STUDENT/ALUMNI)"}
                         409 {"message":"이미 사용 중인 이메일입니다."}
                    """)
    @PatchMapping("/api/admin/users/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "수정할 회원 id", example = "80") @PathVariable Long userId,
            @RequestBody UserUpdateRequest request) {
        log.info("회원 정보 수정 요청 - userId: {}, 데이터: {}", userId, request);
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    // 회원 정지 (temporary) - 단일 회원, 종료일까지 - 관리자 전용
    @Operation(summary = "회원 정지 (관리자, temporary, PATCH)",
            description = """
                    회원목록 페이지에서 회원을 기간 정지시킬 때 호출한다. 전달한 종료일 23:59:59 까지 정지(temporary)되며,
                    수동 조치이므로 ban_log 에 source=manual, warning_no=NULL 로 기록된다(경고 누적으로 집계되지 않음).

                    ### 요청 예시
                    ```json
                    {"endDate":"2026-09-30"}
                    ```
                    - 종료일(YYYY-MM-DD) 23:59:59 까지 정지된다.

                    ### 응답 예시
                    ```json
                    {"message":"회원이 정지되었습니다.","userId":80}
                    ```

                    실패: 400 {"message":"정지 종료일은 현재보다 미래여야 합니다."}
                         409 {"message":"이미 영구차단된 회원입니다. 정지로 변경할 수 없습니다."}
                    """)
    @PatchMapping("/api/admin/users/{userId}/suspend")
    public ResponseEntity<UserResponse> suspendUser(
            @Parameter(description = "정지할 회원 id", example = "80") @PathVariable Long userId,
            @RequestBody UserSuspendRequest request) {
        log.info("회원 정지 요청 - userId: {}, 데이터: {}", userId, request);
        return ResponseEntity.ok(userService.suspendUser(userId, request));
    }

    // 회원 영구차단 (permanent) - 단일/다중 회원 - 관리자 전용
    @Operation(summary = "회원 영구차단 (관리자, 단일/다중, PATCH)",
            description = """
                    회원목록 페이지에서 회원을 영구차단(permanent)할 때 호출한다. 단일/다중 모두 userIds 배열로 받으며,
                    없는 id가 하나라도 있으면 전체가 실패한다(all-or-nothing).

                    ### 요청 예시
                    ```json
                    {"userIds":[80,81,82]}
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"영구차단 처리되었습니다.","userId":null}
                    ```

                    실패: 404 {"message":"존재하지 않는 회원입니다."} (없는 id가 하나라도 있으면 전체 실패)
                    """)
    @PatchMapping("/api/admin/users/ban")
    public ResponseEntity<UserResponse> banUsers(@RequestBody UserBanRequest request) {
        log.info("회원 영구차단 요청 - 데이터: {}", request);
        return ResponseEntity.ok(userService.banUsers(request));
    }
}
