package com.example.rednote.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebStaticConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把 URL 中的 /images/** 映射到本地目录 D:/rednoteUpload/images/
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:D:/rednoteUpload/images/");
    }
}