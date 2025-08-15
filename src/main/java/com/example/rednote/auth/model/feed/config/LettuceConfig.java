package com.example.rednote.auth.model.feed.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;


@Configuration
public class LettuceConfig {

    @Value("${spring.redis.data.host}")
    private String host;

    @Value("${spring.redis.data.port}")
    private int port;

    @Bean
    public RedisClient redisClient() {
        return RedisClient.create("redis://" + host + ":" + port);
    }

    // @Bean
    // public RedisClusterClient redisClusterClient() {
    //     List<RedisURI> nodes = Arrays.asList(
    //         RedisURI.create("redis://node1:6379"),
    //         RedisURI.create("redis://node2:6379"),
    //         RedisURI.create("redis://node3:6379")
    //     );
    //     return RedisClusterClient.create(nodes);
    // }
}