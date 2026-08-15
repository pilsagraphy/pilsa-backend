package com.back.auth.controller;

import com.back.auth.dto.RoleResponse;
import com.back.auth.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "공통(내 신분·권한)",
        description = "로그인한 사용자의 신분(재학생/졸업생)과 관리 권한 레벨을 내려주는 공통 API. 프론트 화면 분기의 기준값.")
public class RoleController {

  private final RoleService roleService;

  // 로그인한 사용자의 신분·관리권한 조회.
  // 1기에는 {"role":"STUDENTS"} 하나였지만 users.role 이 member_type + admin_level 2축으로 갈렸으므로
  // 재학생/졸업생(memberType)과 관리 레벨(adminLevel)을 함께 내려준다.
  @Operation(summary = "로그인 사용자 신분·관리권한 조회",
          description = """
                  로그인 직후·페이지 진입 시 프론트가 메뉴/버튼 노출을 분기하기 위해 호출한다.
                  1기 응답은 {"role":"STUDENTS"} 하나였지만, users.role 컬럼이 member_type + admin_level 2축으로
                  갈리면서 두 값을 함께 내려준다. 경로는 1기와 동일하게 /api/role 을 유지한다.

                  ### 요청 예시
                  ```
                  GET /api/role
                  ```
                  쿼리 없음. Authorization 헤더(액세스 토큰) 필요.

                  ### 응답 예시
                  ```json
                  {"memberType":"STUDENT","adminLevel":0}
                  ```
                  - memberType: STUDENT(재학생) / ALUMNI(졸업생)
                  - adminLevel: 0=일반회원, 1~3=관리자

                  실패: 401 {"message":"..."} (미인증)
                  """)
  @GetMapping("/api/role")
  public RoleResponse getMyRole() {
    return roleService.getCurrentUserRole();
  }
}
