package com.moai.works.web.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.moai.works.web.relay.Problems;

/** BFF 자신의 입력 오류도 api 와 같은 problem+json 으로. */
@RestControllerAdvice
public class ErrorAdvice {

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<String> invalid(Exception e) {
    return ResponseEntity.badRequest().header("Content-Type", "application/problem+json;charset=UTF-8")
        .body(Problems.json(400, "INVALID_REQUEST", "입력값을 확인하십시오."));
  }
}
