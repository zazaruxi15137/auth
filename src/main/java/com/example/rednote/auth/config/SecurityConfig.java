package com.example.rednote.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.example.rednote.auth.security.filter.JwtAuthenticationFilter;
import com.example.rednote.auth.security.handler.MyLogoutSuccessfulHandler;
import com.example.rednote.auth.security.handler.UserAccessDeniedHandler;
import com.example.rednote.auth.security.handler.UserAuthenticationEntryPoint;
import com.example.rednote.auth.security.handler.UserLogoutHandler;
import com.example.rednote.auth.security.service.MyUserDetailsService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true) 
public class SecurityConfig {

    private final MyUserDetailsService myUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserAccessDeniedHandler userAccessDeniedHandler;
    private final UserAuthenticationEntryPoint userAuthenticationEntryPoint;
    private final UserLogoutHandler userLogoutHandler;
    private final MyLogoutSuccessfulHandler myLogoutSuccessfulHandler;
    /**
     * 配置安全过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 关闭 csrf (测试接口时可先关闭)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 配置请求权限
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()// 允许注册和登录接口匿名访问
                    .requestMatchers("/api/error", "/error").permitAll() // 允许错误处理接口匿名访问
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui.html").permitAll()
                    .requestMatchers("/images/**").permitAll()
                    .anyRequest().authenticated()) // 其他请求需要认证
            .addFilterBefore(jwtAuthenticationFilter, LogoutFilter.class)
            .userDetailsService(myUserDetailsService)
            .exceptionHandling(e -> e
                    .accessDeniedHandler(userAccessDeniedHandler)
                    .authenticationEntryPoint(userAuthenticationEntryPoint))
            .logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    .addLogoutHandler(userLogoutHandler)
                    .logoutSuccessHandler(myLogoutSuccessfulHandler)
                    .deleteCookies("JSESSIONID"));

        return http.build();
    }

    /**
     * 配置密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
     @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
