package com.example.gateway_service.gateway_service.common.aop;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target; 

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    /**
     * 幂等键的 SpEL 表达式（仅服务端硬编码）
     * 可用变量：
     *  - #headers  请求头 Map（小写）
     *  - #params   查询/表单参数 Map
     *  - #path     路由模板变量 Map
     *  - #req      HttpServletRequest
     *  - #args     方法入参数组
     *  - #userId   当前用户ID（切面里提供）
     */
    // "#headers['idempotency-key'] ?: " +
    //     "#headers['x-idempotency-key'] ?: " +
    //     "#headers['x-request-id'] ?: " +
    //     "#params['clientReqId'] ?: " +
    //     "#params['requestId']";
    String key() default
        "#headers['idempotency-key'] ?: " +
        "#headers['x-idempotency-key'] ?: " ;

    /** 幂等窗口（秒）：占位/完成状态与响应缓存的 TTL */
    long ttlSeconds() default 900; // 15 分钟

    /** 是否缓存并回放响应体（JSON） */
    boolean storeResponse() default true;

    /** 是否强制要求客户端提供幂等键（缺失直接 428/400） */
    boolean required() default true;
}
