package com.example.rednote.auth.model.feed.listener;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;

import com.example.rednote.auth.model.feed.handler.NotePushHandler;


import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedCnsumerListener {

    private final StringRedisTemplate redis;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final NotePushHandler handler; // 你的业务逻辑（见第3节）
    @Value("${app.feed.stream.name}") private String stream;
    @Value("${app.feed.stream-group}") private String group;
    @Value("${app.feed.stream.consumers}") private int consumers; // 并发消费者数量

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        // 1) 确保流与消费组存在（占位 + 精确删除）
        RecordId placeholder = null;
        try {
            placeholder = redis.opsForStream().add(stream, Map.of("init", "1"));
            redis.opsForStream().createGroup(stream, ReadOffset.latest(), group);
            log.info("");
        } catch (RedisSystemException e) {
            if (e.getCause().getMessage() == null || !e.getCause().getMessage().contains("BUSYGROUP")) {
                log.error("failed to create group:Redis:{}:{}", e.getMessage(), e.getCause().getMessage());
                throw e; // 非组已存在错误抛出
            }
        } finally {
            if (placeholder != null) {
                try { redis.opsForStream().delete(stream, placeholder); }
                catch (DataAccessException ignore) {}
            }
        }
        
            // 2) 注册并发监听（c1..cN）
        for (int i = 1; i <= consumers; i++) {
            String consumerName = "c" + i;
            Subscription sub = container.receive(
                    Consumer.from(group, consumerName),
                    StreamOffset.create(stream, ReadOffset.lastConsumed()),
                    rec -> handleMessage(rec));
            subscriptions.add(sub);
        }
        log.info("Stream listeners started: group={}, consumers={}", group, consumers);
    }

    private void handleMessage(MapRecord<String, String, String> rec) {
        try {
            // Thread currentThread = Thread.currentThread();
            // log.info("Thread: {}, recordId={}", currentThread.getName(), rec.getId());
            Map<String, String> m =  rec.getValue();
            long authorId = Long.parseLong(Objects.toString(m.get("authorId")));
            long noteId   = Long.parseLong(Objects.toString(m.get("noteId")));
            long ts       = Long.parseLong(Objects.toString(m.get("tsMillis")));

            handler.handle(authorId, noteId, ts);
            redis.opsForStream().acknowledge(stream, group, rec.getId());
            redis.opsForStream().delete(rec);
        } catch (Exception e) {
            log.error("handle failed, recordId={},ex={}.消息pending", rec.getId(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Subscription sub : subscriptions) {
            try { sub.cancel(); } catch (Exception ignore) {}
        }
        subscriptions.clear();
        log.info("Stream listeners stopped.");
    }
}