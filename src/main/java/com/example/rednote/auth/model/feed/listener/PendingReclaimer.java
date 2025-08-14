package com.example.rednote.auth.model.feed.listener;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.model.feed.handler.NotePushHandler;
import com.example.rednote.auth.model.feed.service.PendingClaimService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// ---- Lettuce 底层 API ----
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import jakarta.annotation.PostConstruct;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingReclaimer {

    private final PendingClaimService pendingClaimService;

    @Value("${app.feed.reclaim.fixed-delay-ms}") private long fixedDelay;
    @Qualifier("reclaimExecutor") 
    private final ScheduledExecutorService  reclaimExecutor;
    @PostConstruct
    public void init() {
        reclaimExecutor.scheduleWithFixedDelay(
            this::safeReclaimWrapper,
            0,
            fixedDelay,
            TimeUnit.MILLISECONDS);
        log.info("Feed pending reclaimer started via feedExecutor.");
    }
    private void safeReclaimWrapper() {
        try {
            pendingClaimService.reclaimOnce("0-0");
        } catch (Exception e) {
            log.error("Scheduled reclaim task failed", e);
        }
    }

    

}