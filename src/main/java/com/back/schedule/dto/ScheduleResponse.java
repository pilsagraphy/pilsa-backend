package com.back.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//등록/수정/삭제 응답용
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object data; // 단일 객체(id, title 등)를 담는 용도

    public ScheduleResponse(String message) {
        this.message = message;
    }
}
