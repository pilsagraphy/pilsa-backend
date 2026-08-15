package com.back.report.controller;

import com.back.report.dto.ReportRequest;
import com.back.report.dto.ReportResponse;
import com.back.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "신고 접수",
        description = "게시글/댓글 신고 접수. 로그인 회원이면 신분(재학생/졸업생)·관리자 여부와 무관하게 동일하게 사용한다. "
                + "신고 처리(반려/삭제)는 관리자 신고관리(/api/admin/reports/**)가 담당한다.")
public class ReportController {

    private final ReportService reportService;

    // 게시글/댓글 신고 접수
    // 경로에 stu/alu 구분을 두지 않는다 — 신고는 모든 회원 공통 기능이다
    @Operation(summary = "게시글/댓글 신고 접수",
            description = """
                    게시글 상세·댓글 목록의 "신고" 버튼에서 신고 사유를 선택해 제출할 때 호출한다.
                    관리자든 일반 회원이든 동일하게 이 API 로 접수하며, 관리자의 직접 조치는 별도 관리자 API 를 쓴다.

                    ### 요청 예시
                    ```json
                    {
                      "targetType": "post",
                      "targetId": 171,
                      "reasonId": 1,
                      "detail": "기타 사유일 때만 작성"
                    }
                    ```
                    - targetType: post | comment
                    - detail 은 사유가 '기타'일 때만 작성

                    ### 응답 예시
                    ```json
                    {"message":"신고가 접수되었습니다."}
                    ```

                    실패: 400 {"message":"본인이 작성한 게시글/댓글은 신고할 수 없습니다."}
                    실패: 409 {"message":"이미 신고한 게시글/댓글입니다."} (동일 대상 중복 신고)
                    실패: 409 {"message":"이미 삭제된 게시글/댓글입니다."} (이미 삭제된 대상 신고)
                    실패: 401 {"message":"..."} (미인증)
                    """)
    @PostMapping("/api/user/reports")
    public ResponseEntity<ReportResponse> submitReport(@RequestBody ReportRequest request) {
        log.info("신고 접수 요청 - targetType: {}, targetId: {}", request.getTargetType(), request.getTargetId());
        reportService.submitReport(request);
        return ResponseEntity.ok(new ReportResponse("신고가 접수되었습니다."));
    }
}
