package com.back.auth.exception;

public class AuthException extends RuntimeException {
  public AuthException(String message) {
    super(message);
  }
}

//package com.todolist.auth.exception;
//
//import com.todolist.global.exception.BaseException;
//import org.springframework.http.HttpStatus;
//
//public class AuthException extends BaseException {
//  public AuthException(String message, HttpStatus status) {
//    super(message, status);
//  }
//}
//
