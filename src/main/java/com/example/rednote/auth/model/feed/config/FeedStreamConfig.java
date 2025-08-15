package com.example.rednote.auth.model.feed.config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
@Configuration
public class FeedStreamConfig {

    @Bean
    StreamMessageListenerContainer<String, MapRecord<String, String, String>> feedStreamContainer(
            RedisConnectionFactory connectionFactory,
            @Qualifier("feedExecutor") TaskExecutor feedExecutor,
            @Value("${app.feed.stream.poll-timeout-seconds}") long pollTimeoutSeconds,
            @Value("${app.feed.stream.batch-size}") int batchSize
    ) {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .executor(feedExecutor)                       // 复用线程池
                .batchSize(batchSize)                         // 每次最多从Redis取几条
                .pollTimeout(Duration.ofSeconds(pollTimeoutSeconds)) // XREADGROUP BLOCK 超时
                .errorHandler(t -> {
                    // 建议打日志/告警
                    log.error("stream error", t);
                })
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.start();
        return container;
    }
}