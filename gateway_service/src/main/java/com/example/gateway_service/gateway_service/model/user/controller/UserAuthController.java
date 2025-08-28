package com.example.gateway_service.gateway_service.model.user.controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.gateway_service.gateway_service.common.RespondMessage;
import com.example.gateway_service.gateway_service.common.aop.Idempotent;
import com.example.gateway_service.gateway_service.common.tool.KeysUtil;
import com.example.gateway_service.gateway_service.common.tool.RedisUtil;
import com.example.gateway_service.gateway_service.model.user.dto.LoginDto;
import com.example.gateway_service.gateway_service.model.user.dto.RegisterUserDto;
import com.example.gateway_service.gateway_service.model.user.dto.UserDto;
import com.example.gateway_service.gateway_service.model.user.entity.User;
import com.example.gateway_service.gateway_service.model.user.service.UserFollowService;
import com.example.gateway_service.gateway_service.model.user.service.UserService;
import com.example.gateway_service.gateway_service.security.service.PermissionRoleService;
import com.example.gateway_service.gateway_service.security.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "用户管理", description = "用户相关API")
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {
    // 注入 UserService
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final AuthenticationManager authenticationManager;
    private final PermissionRoleService permissionRoleService;
    private final UserFollowService UserFollowServiceImpl;
    @Value("${app.feed.bigv-threshold}")
    private Long bigvThreshold;
    @Value("${app.feed.follow-bigV-exprir}")
    private Long expiration;
    

    /*
     * Admin 注册接口
     */
    @Operation(summary = "Admin注册接口",description = "只有Admin账户才能访问")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register/admin")
    @Idempotent()
    public ResponseEntity<Object> registerAdmin(@RequestBody @Valid RegisterUserDto registerUserDto) {
        if (userService.existsByEmail(registerUserDto.getEmail()) || userService.existsByUsername(registerUserDto.getUsername())) {
             return ResponseEntity.status(400).body(RespondMessage.fail("username or email exists:用户名或者邮箱已存在"));
        }
        String encodedPassword = passwordEncoder.encode(registerUserDto.getPassword());
        registerUserDto.setPassword(encodedPassword);
        User user = userService.registerUserWithRoles(registerUserDto.toUser(), "ROLE_ADMIN");
        // 注册为超级管理员
        log.info("注册管理员用户: {},用户id: {}", user.getUsername(), user.getId());
        return ResponseEntity.ok().body(RespondMessage.success("register success:注册成功"));
    }
    /*
     * 普通用户注册接口
     */
    @Operation(summary = "普通用户注册接口",description = "注册为普通用户")
    @PostMapping("/register")
    @Idempotent()
    public ResponseEntity<Object> registerUser(@RequestBody @Valid RegisterUserDto registerUserDto) {
        if (userService.existsByEmail(registerUserDto.getEmail()) || userService.existsByUsername(registerUserDto.getUsername())) {
             return ResponseEntity.status(400).body(RespondMessage.fail("username or email exists:用户名或者邮箱已存在"));
        }
        String encodedPassword = passwordEncoder.encode(registerUserDto.getPassword());
        registerUserDto.setPassword(encodedPassword);
        //注册为普通用户
        User user=userService.registerUserWithRoles(registerUserDto.toUser(),"ROLE_USER");
        log.info("注册用户: {},用户id: {}", user.getUsername(), user.getId());
        return ResponseEntity.ok().body(RespondMessage.success("register success:注册成功"));
    }
    /*
     * 登录接口
     */
    @PostMapping("/login")
    public ResponseEntity<Object> loginPage(@RequestBody @Valid LoginDto loginUserDto) throws JsonProcessingException {
        // 验证用户名密码是否正确
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginUserDto.getUsername(), loginUserDto.getPassword())
        );


        // 验证通过获取用户实体生成token
        if (!authentication.isAuthenticated()) {
            log.warn("用户登录失败: {}", loginUserDto.getUsername());
            return ResponseEntity.status(400).body(RespondMessage.fail("username or password incorrect:用户名或密码错误"));
        }
            // 获取用户实体
        UserDto userDto = userService.findByUsername(loginUserDto.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("user not found:用户不存在"))
            .toUserDto();

        // 根据roles获取permissons
        List<String> permissions=permissionRoleService.getPermissionByRoles(userDto.getRoles());

        if(permissions.isEmpty()) {
            log.warn("权限获取失败: {}", loginUserDto.getUsername());
            return ResponseEntity.status(400).body(RespondMessage.fail("login failed cant get permission:登录失败"));
        }
        Long userId=userDto.getId();
        // 设置返回信息
        userDto.setPermission(permissions);
        if (!redisUtil.hasKey(KeysUtil.redisFollowedBigVKey(userId))) {
                List<Long> bigvAuthors = UserFollowServiceImpl.findBigvAuthors(userId, bigvThreshold);
                if (!bigvAuthors.isEmpty()) {
                        redisUtil.setAllToSet(
                                KeysUtil.redisFollowedBigVKey(userId),
                                bigvAuthors.stream().map(String::valueOf).toArray(String[]::new),
                                expiration,
                                TimeUnit.HOURS);
                log.info("已刷新粉丝关注的大V:{}", bigvAuthors.size());
                        }}
        
        // 保存到redis中
        log.info("用户登录成功: userId={}, username={}, roles={}", userDto.getId(), userDto.getUsername(), userDto.getRoles());

        return ResponseEntity.ok().body(RespondMessage.success("login success:登录成功",jwtUtil.setAuthToken(userDto)));
        }

}
