package com.back.board.report.controller;

import com.back.board.report.dto.ReportReasonResponse;
import com.back.board.report.dto.ReportRequest;
import com.back.board.report.dto.ReportResponse;
import com.back.board.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "신고 접수",
        description = "게시글/댓글 신고 접수. 로그인 회원이면 신분(재학생/졸업생)·관리자 여부와 무관하게 동일하게 사용한다. "
                + "신고 처리(반려/삭제)는 관리자 신고관리(/api/admin/reports/**)가 담당한다.")
public class ReportController {

    private final ReportService reportService;

    // 신고 사유 카테고리 목록 (신고 모달 셀렉트바) — reasons 테이블 노출, FE 하드코딩 제거용
    @Operation(summary = "신고 사유 카테고리 목록",
            description = """
                    게시글/댓글 신고 모달의 사유 셀렉트바를 그릴 때 호출한다. reasons 테이블을 노출 순서대로 내려주며,
                    프론트가 사유 목록을 하드코딩하지 않도록 한다.

                    ### 요청 예시
                    ```
                    GET /api/user/reports/reasons
                    ```
                    (본문 없음)

                    ### 응답 예시
                    ```json
                    [
                      {"reasonId":1,"code":"ABUSE","label":"욕설/비방","displayOrder":1},
                      {"reasonId":8,"code":"ETC","label":"기타","displayOrder":8}
                    ]
                    ```
                    ※ code=ETC 일 때만 신고 접수 시 detail 입력이 필요하다.
                    """)
    @GetMapping("/api/user/reports/reasons")
    public ResponseEntity<List<ReportReasonResponse>> getReasons() {
        log.info("신고 사유 카테고리 목록 조회");
        return ResponseEntity.ok(reportService.getReasons());
    }

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
                    - detail 은 사유가 '기타'(code=ETC)일 때만 작성한다. **서버가 검증한다** —
                      기타인데 비어 있으면 400, 기타가 아닌데 값이 있으면 400(공백만 있으면 미입력으로 본다).

                    ### 응답 예시
                    ```json
                    {"message":"신고가 접수되었습니다."}
                    ```

                    실패: 400 {"message":"본인이 작성한 게시글/댓글은 신고할 수 없습니다."}
                    실패: 400 {"message":"'기타' 사유는 상세 내용을 입력해 주세요."}
                    실패: 400 {"message":"상세 내용은 '기타' 사유일 때만 입력할 수 있습니다."}
                    실패: 400 {"message":"상세 내용은 500자 이하로 입력해 주세요."}
                    실패: 400 {"message":"존재하지 않거나 사용하지 않는 신고 사유입니다."}
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
