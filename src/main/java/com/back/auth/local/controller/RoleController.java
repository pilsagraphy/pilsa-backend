package com.back.auth.local.controller;

import com.back.auth.local.dto.RoleResponse;
import com.back.auth.local.dto.UserNameResponse;
import com.back.auth.local.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "공통(내 정보)",
        description = "로그인한 사용자의 신분(재학생/졸업생)·관리 권한 레벨·이름을 내려주는 공통 API. 프론트 화면 분기와 헤더 표기의 기준값.")
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

  // 헤더 "OOO님" 표기용. 신분·권한(/api/role)과 갱신 주기가 같아 함께 호출되지만,
  // 이름은 개인정보라 응답을 분리해 둔다 — 화면 분기값만 필요한 곳에서 이름까지 받지 않게 하기 위함.
  @Operation(summary = "로그인 사용자 이름 조회",
          description = """
                  헤더·마이페이지 진입부의 "OOO님" 표기에 쓴다. 본인 이름만 조회되며 다른 회원은 조회할 수 없다.
                  (다른 회원의 이름은 게시글·댓글 응답의 authorName 으로만 노출되고, 익명 글이면 '익명'으로 마스킹된다.)

                  ### 요청 예시
                  ```
                  GET /api/user/name
                  ```
                  쿼리 없음. Authorization 헤더(액세스 토큰) 필요.

                  ### 응답 예시
                  ```json
                  {"name":"홍길동"}
                  ```

                  실패: 401 {"message":"인증이 필요합니다. (Authorization 헤더 누락 또는 유효하지 않은 토큰)"}
                       404 {"message":"사용자 정보를 찾을 수 없습니다."} (탈퇴 처리된 계정의 토큰으로 호출한 경우)
                  """)
  @GetMapping("/api/user/name")
  public UserNameResponse getMyName() {
    return roleService.getCurrentUserName();
  }
}
