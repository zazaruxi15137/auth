package com.example.rednote.auth.model.admin.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.rednote.auth.common.service.BlockingTokenBucket;
import com.example.rednote.auth.common.properties.LocalBucketProperties;

@Tag(name = "本机令牌桶（阻塞式）")
@RestController
@RequestMapping("/admin/local-bucket")
@RequiredArgsConstructor
public class LocalBucketAdminController {

    private final BlockingTokenBucket bucket;
    private final LocalBucketProperties props;

    @Operation(summary = "查看状态")
    @GetMapping("/stats")
    public Stats stats() {
        Stats s = new Stats();
        s.capacity = props.getCapacity();
        s.ratePerSecond = props.getRatePerSecond();
        s.refillPeriodMs = props.getRefillPeriodMs();
        s.available = bucket.availableTokens();
        s.acquireTimeoutMs = props.getAcquireTimeoutMs();
        s.enabled = props.isEnabled();
        return s;
    }

    @Operation(summary = "动态调整参数（非空才生效）")
    @PostMapping("/update")
    public ResponseEntity<?> update(@RequestBody UpdateReq req) {
        if (req.capacity != null) {
            bucket.setCapacity(req.capacity);
            props.setCapacity(req.capacity);
        }
        if (req.ratePerSecond != null) {
            bucket.setRatePerSecond(req.ratePerSecond);
            props.setRatePerSecond(req.ratePerSecond);
        }
        if (req.refillPeriodMs != null) {
            bucket.setRefillPeriodMillis(req.refillPeriodMs);
            props.setRefillPeriodMs(req.refillPeriodMs);
        }
        if (req.acquireTimeoutMs != null) props.setAcquireTimeoutMs(req.acquireTimeoutMs);
        if (req.enabled != null) props.setEnabled(req.enabled);
        return ResponseEntity.ok().build();
    }

    @Data public static class UpdateReq {
        Integer capacity;
        Double ratePerSecond;
        Long refillPeriodMs;
        Long acquireTimeoutMs;
        Boolean enabled;
    }
    @Data public static class Stats {
        int capacity;
        double ratePerSecond;
        long refillPeriodMs;
        int available;
        long acquireTimeoutMs;
        boolean enabled;
    }
}
