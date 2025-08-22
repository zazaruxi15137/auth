package com.example.rednote.auth.common.service;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import com.example.rednote.auth.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LuaScriptService implements ResourceLoaderAware{

  
    private ResourceLoader resourceLoader; 
    
    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    /**
     * Lua：去重 + ZADD + 裁剪
     * KEYS[1] inboxKey
     * KEYS[2] dedupKey
     * ARGV[1]=scoreMillis, ARGV[2]=member(noteId), ARGV[3]=maxSize, ARGV[4]=dedupTtl
     * return 1=写入成功(首次), 0=重复
     */
    @Bean
    DefaultRedisScript<Long> inboxUpsertScript() {
        Resource resource = resourceLoader.getResource("classpath:lua/feedscript.lua");  
        String lua;  
        try {  
            lua = new String(resource.getInputStream().readAllBytes());  
        } catch (Exception e) {  
            throw new CustomException("Unable to read Lua script file.");  
        } 

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }
    @Bean
    DefaultRedisScript<Long> notePublishScript() {
        Resource resource = resourceLoader.getResource("classpath:lua/notepublish.lua");  
        String lua;  
        try {  
            lua = new String(resource.getInputStream().readAllBytes());  
        } catch (Exception e) {  
            log.error("Unable to read Lua script file.");
            throw new CustomException("内部错误");  
        } 

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }
    @Bean
    DefaultRedisScript<Long> followPullScript() {
        Resource resource = resourceLoader.getResource("classpath:lua/followpull.lua");  
        String lua;  
        try {  
            lua = new String(resource.getInputStream().readAllBytes());  
        } catch (Exception e) {  
            log.error("Unable to read Lua script file.");
            throw new CustomException("内部错误");  
        } 

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }
}
     

    
    