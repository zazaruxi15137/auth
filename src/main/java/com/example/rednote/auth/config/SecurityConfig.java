package com.example.rednote.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.example.rednote.auth.filter.JwtAuthenticationFilter;
import com.example.rednote.auth.handler.MyLogoutSuccessfulHandler;
import com.example.rednote.auth.handler.UserAccessDeniedHandler;
import com.example.rednote.auth.handler.UserAuthenticationEntryPoint;
import com.example.rednote.auth.handler.UserLogoutHandler;
import com.example.rednote.auth.service.UserService;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true) 
public class SecurityConfig {

    private final UserService userService;
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
                    .requestMatchers("/api/auth/error", "/error").permitAll() // 允许错误处理接口匿名访问
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated()) // 其他请求需要认证
            .addFilterBefore(jwtAuthenticationFilter, LogoutFilter.class)
            .userDetailsService(userService)
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

    // @Bean
    // public DaoAuthenticationProvider daoAuthenticationProvider() {
       
    //     DaoAuthenticationProvider provider= new DaoAuthenticationProvider(userService);
    //     provider.setPasswordEncoder(passwordEncoder());
    //     return provider; 
    // }
 
    

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
