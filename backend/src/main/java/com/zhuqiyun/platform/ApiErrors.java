package com.zhuqiyun.platform;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> status(ResponseStatusException e) {
        return failure(e.getStatusCode().value(),e.getReason() == null ? "请求失败" : e.getReason());
    }
    @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<?> conflict(DataIntegrityViolationException e) { return failure(409,"记录重复、数据不完整或仍被业务单据引用"); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentNotValidException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    public ResponseEntity<?> invalid(Exception e) { return failure(400,"请求字段、类型或格式无效"); }
    @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<?> denied(Exception e) { return failure(403,"无权访问"); }
    @ExceptionHandler(ServiceException.class) public ResponseEntity<?> business(ServiceException e) {
        return ResponseEntity.badRequest().body(CommonResult.error(e.getCode(),e.getMessage()));
    }
    @ExceptionHandler(Exception.class) public ResponseEntity<?> unexpected(Exception e) {
        if (e instanceof ErrorResponse response) return failure(response.getStatusCode().value(),"请求失败");
        log.error("API request failed ({})",e.getClass().getSimpleName());
        return failure(500,"服务器处理失败，请联系管理员");
    }
    private ResponseEntity<?> failure(int status,String message) { return ResponseEntity.status(status).body(CommonResult.error(status,message)); }
}
