package com.skala.minutes.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 실패가 그대로 500 으로 나가면 훈련생도 사용자도 원인을 알 수 없다.
 * 다시 시도해서 될 실패와, 다시 시도해도 안 될 실패를 나눠서 알려 준다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("요청이 올바르지 않다");
        return ResponseEntity.badRequest().body(new ErrorResponse(msg));
    }

    // 재시도를 다 쓰고도 실패한 일시적 오류 — Rate Limit, 모델 서버 오류
    @ExceptionHandler(TransientAiException.class)
    public ResponseEntity<ErrorResponse> handleTransient(TransientAiException e) {
        log.warn("AI 일시적 오류, 재시도 소진: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("모델이 지금 불안정하다. 잠시 후 다시 시도한다"));
    }

    // 다시 시도해도 안 되는 오류 — 키가 틀렸거나 요청이 잘못된 경우
    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<ErrorResponse> handleNonTransient(NonTransientAiException e) {
        log.error("AI 오류, 설정 확인이 필요하다: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("모델 호출 설정을 확인한다. OPENAI_API_KEY 부터 본다"));
    }
}
