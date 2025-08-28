package com.example.gateway_service.gateway_service.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Bean(name = "cleanupExecutor")
    public Executor cleanupExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("cleanup-");
        exec.initialize();
        log.info("cleanupExecutor initialized");
        return exec;
    }

    @Bean(name = "serviceExecutor")
    public TaskExecutor serviceExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(5);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("service-");
        exec.initialize();
        log.info("serviceExecutor initialized");
        return exec;
    }

    @Bean(name = "feedExecutor")
    public TaskExecutor feedExecutor() {

        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(5);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(150);
        exec.setThreadNamePrefix("note-push-consumer-");
        exec.initialize();
        log.info("feedExecutor initialized");
        return exec;
    }
    // @Bean(name = "reclaimExecutor")
    // public ScheduledExecutorService reclaimExecutor() {
    //     return Executors.newScheduledThreadPool(2, runnable -> {
    //         Thread t = new Thread(runnable);
    //         t.setName("feed-reclaimer-thread");
    //         t.setDaemon(true);
    //         return t;
    //     });
    // }
}
