package com.example.rednote.auth.handler;

import java.util.HashMap;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.example.rednote.auth.exception.CustomException;
import com.example.rednote.auth.tool.RespondMessage;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import io.jsonwebtoken.security.SignatureException;
@RestControllerAdvice
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
        return new RespondMessage<Map<String, String>>("参数校验失败"
        , HttpStatus.BAD_REQUEST.value()
        , errors);
    }

    /**
     * 处理签名异常
     */
    @ExceptionHandler(SignatureException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)    
    public RespondMessage<String> handleSignatureException(SignatureException ex) {
        return new RespondMessage<>("非法令牌"
        , HttpStatus.UNAUTHORIZED.value()
        , ex.getMessage());
    }
    /**
     * 处理自定义异常
     */ 
    @ExceptionHandler(CustomException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespondMessage<String> handleCustomException(CustomException ex) {
        return new RespondMessage<>("自定义异常"
        , HttpStatus.BAD_REQUEST.value()
        , ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public RespondMessage<String> handleBadCredentialsException(BadCredentialsException ex) {
        return new RespondMessage<>("认证失败,用户名密码错误"
        , HttpStatus.UNAUTHORIZED.value()
        , ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespondMessage<String> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        return new RespondMessage<>("缺少必要的请求参数"
        , HttpStatus.BAD_REQUEST.value()
        , ex.getMessage());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public RespondMessage<String> handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        return new RespondMessage<>("权限不足"
        , HttpStatus.FORBIDDEN.value()
        , ex.getMessage());
    }

    /**
     * 处理所有其他未捕获异常
     */
    // @ExceptionHandler(Exception.class)
    // public RespondMessage<String> handleException(Exception ex) {
    //     return new RespondMessage<>("服务器内部错误"
    //                     , HttpStatus.INTERNAL_SERVER_ERROR.value()
    //                     , ex.getClass().getName() + ": " + ex.getMessage());
    // }
}
