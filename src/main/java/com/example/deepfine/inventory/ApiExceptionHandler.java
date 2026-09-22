package com.example.deepfine.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InventoryException.class)
    public ProblemDetail handleInventory(InventoryException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDatabase(DataAccessException exception) {
        log.error("Database operation failed", exception);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "데이터베이스 요청을 처리하지 못했습니다.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status,
                status.value() == 400 ? "요청 형식, 상품명, 수량 또는 상품 ID를 확인해 주세요."
                        : "요청을 처리할 수 없습니다.");
        detail.setProperty("code", status.value() == 400 ? "INVALID_REQUEST" : "HTTP_" + status.value());
        return super.handleExceptionInternal(exception, detail, headers, status, request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setProperty("code", code);
        return detail;
    }
}
