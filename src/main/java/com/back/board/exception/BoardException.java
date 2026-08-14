package com.back.board.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

// 게시판(공지/자유/정보) 통합 예외
public class BoardException extends BaseException {

    public BoardException(String message, HttpStatus status) {
        super(message, status);
    }
}
