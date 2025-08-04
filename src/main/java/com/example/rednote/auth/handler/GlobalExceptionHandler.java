package com.example.rednote.auth.handler;

import java.util.HashMap;
import org.springframework.http.HttpStatus;
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
import com.example.rednote.auth.exception.CustomException;
import com.example.rednote.auth.tool.RespondMessage;
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
    public RespondMessage<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("参数校验失败: {}", errors);
        return new RespondMessage<Map<String, String>>("参数校验失败"
        , HttpStatus.BAD_REQUEST.value()
        , null);
    }

    /**
     * 处理签名异常
     */
    @ExceptionHandler(SignatureException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)    
    public RespondMessage<String> handleSignatureException(SignatureException ex) {
        log.warn("JWT签名验证失败: {}", ex.getMessage());
        return new RespondMessage<>("非法令牌"
        , HttpStatus.UNAUTHORIZED.value()
        , null);
    }
    /**
     * 处理自定义异常
     */ 
    @ExceptionHandler(CustomException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespondMessage<String> handleCustomException(CustomException ex) {
        log.warn("自定义异常: {}", ex.getMessage());
        return new RespondMessage<>(ex.getMessage()
        , HttpStatus.BAD_REQUEST.value()
        , null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public RespondMessage<String> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("认证失败: {}", ex.getMessage());
        return new RespondMessage<>("认证失败,用户名密码错误"
        , HttpStatus.UNAUTHORIZED.value()
        , null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespondMessage<String> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("缺少必要的请求参数: {}", ex.getMessage());
        return new RespondMessage<>("缺少必要的请求参数"
        , HttpStatus.BAD_REQUEST.value()
        , null);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public RespondMessage<String> handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        log.warn("权限不足: {}", ex.getMessage());
        return new RespondMessage<>("权限不足"
        , HttpStatus.FORBIDDEN.value()
        , null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public RespondMessage<String> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.warn("不支持的请求方法: {}", ex.getMessage());
        return new RespondMessage<>("不支持的请求方法"
        , HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()
        , null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public RespondMessage<String> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("不存在的请求路径: {}", ex.getMessage());
        return new RespondMessage<>("NOT FOUND"
        , HttpStatus.NOT_FOUND.value()
        , null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public RespondMessage<String> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        log.warn("不支持的参数格式: {}", ex.getMessage());
        return new RespondMessage<>("不支持的参数格式"
        , HttpStatus.BAD_REQUEST.value()
        , null);
    }
    
@ExceptionHandler(HttpMessageNotReadableException.class)
    public RespondMessage<String> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("请求体丢失: {}", ex.getMessage());
        return new RespondMessage<>("请求体丢失"
        , HttpStatus.BAD_REQUEST.value()
        , null);
    }
    @ExceptionHandler(JsonProcessingException.class)
    public RespondMessage<String> handleJsonProcessingException(JsonProcessingException ex) {
        log.error("实体转化为Json失败{}", ex.getMessage());
        return new RespondMessage<>("服务器内部错误"
        , HttpStatus.BAD_REQUEST.value()
        , null);
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
