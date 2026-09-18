package com.workflowtest.server.api;

import com.workflowtest.server.application.AssetService.ConflictException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ConflictException.class)
    ResponseEntity<?> conflict(ConflictException e) { return error(HttpStatus.CONFLICT, "REVISION_CONFLICT", e.getMessage()); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied(AccessDeniedException e) { return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<?> badRequest(Exception e) { return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message(e)); }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicate(DuplicateKeyException e) { return error(HttpStatus.CONFLICT, "DUPLICATE", "数据编码或名称已存在"); }

    private ResponseEntity<?> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now().toString(), "status", status.value(), "code", code, "message", message));
    }
    private String message(Exception e) {
        if (e instanceof MethodArgumentNotValidException validation && validation.getBindingResult().getFieldError() != null)
            return validation.getBindingResult().getFieldError().getField() + ": " + validation.getBindingResult().getFieldError().getDefaultMessage();
        return e.getMessage() == null ? "请求参数无效" : e.getMessage();
    }
}
