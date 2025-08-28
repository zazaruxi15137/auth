package com.example.gateway_service.gateway_service.common.handler;

import java.util.HashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.example.gateway_service.gateway_service.common.RespondMessage;
import com.example.gateway_service.gateway_service.common.exception.CustomException;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import io.jsonwebtoken.security.SignatureException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("参数校验失败: {}", errors);
        return ResponseEntity.badRequest().body(RespondMessage.fail("参数校验失败"));
    }

    /**
     * 处理签名异常
     */
    @ExceptionHandler(SignatureException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)    
    public ResponseEntity<Object> handleSignatureException(SignatureException ex) {
        log.warn("JWT签名验证失败: {}", ex.getMessage());
        return ResponseEntity.status(401).body(RespondMessage.withcode(HttpStatus.UNAUTHORIZED.value()
        , "非法令牌"
        , null));
    }
    /**
     * 处理自定义异常
     */ 
    @ExceptionHandler(CustomException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Object> handleCustomException(CustomException ex) {
        log.warn("自定义异常: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new RespondMessage<>(ex.getMessage()
        , HttpStatus.BAD_REQUEST.value()
        , null));
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("认证失败: {}", ex.getMessage());
        return ResponseEntity.status(401).body(new RespondMessage<>("认证失败,用户名密码错误"
        , HttpStatus.UNAUTHORIZED.value()
        , null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Object> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("缺少必要的请求参数: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new RespondMessage<>("缺少必要的请求参数"
        , HttpStatus.BAD_REQUEST.value()
        , null));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<Object> handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        log.warn("权限不足: {}", ex.getMessage());
        return ResponseEntity.status(403).body(new RespondMessage<>("权限不足"
        , HttpStatus.FORBIDDEN.value()
        , null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.warn("不支持的请求方法: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(new RespondMessage<>("不支持的请求方法"
                , HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()
                , null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("不存在的请求路径: {}", ex.getMessage());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Object> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        log.warn("不支持的参数格式: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new RespondMessage<>("不支持的参数格式"
        , HttpStatus.BAD_REQUEST.value()
        , null));
    }
    
@ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("请求体丢失: {}", ex.getMessage());
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<Object> handleJsonProcessingException(JsonProcessingException ex) {
        log.error("实体转化为Json失败{}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new RespondMessage<>("服务器内部错误"
                , HttpStatus.INTERNAL_SERVER_ERROR.value()
                , null));
    }
    /**
     * 处理所有其他未捕获异常
     */
    // @ExceptionHandler(Exception.class)
    // public RespondMessage<String> handleException(Exception ex) {
    //     log.error("错误: {}", ex.getClass().getName() + ": " + ex.getMessage());
    //     return new RespondMessage<>("错误"
    //                     , HttpStatus.BAD_REQUEST.value()
    //                     ,  ex.getMessage());
    // }
}
