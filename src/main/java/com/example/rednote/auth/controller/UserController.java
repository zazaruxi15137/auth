package com.example.rednote.auth.controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.rednote.auth.service.PermissionRoleService;
import com.example.rednote.auth.service.UserService;
import com.example.rednote.auth.service.serviceImpl.PermissionRoleServiceImpl;
import com.example.rednote.auth.service.serviceImpl.UserServiceImpl;
import com.example.rednote.auth.tool.RespondMessage;
import com.example.rednote.auth.tool.SerializaUtil;
import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.example.rednote.auth.dto.JwtUserDto;
import com.example.rednote.auth.dto.LoginUserDto;
import com.example.rednote.auth.dto.RegisterUserDto;
import com.example.rednote.auth.dto.UserDto;
import com.example.rednote.auth.entity.User;
import com.example.rednote.auth.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.rednote.auth.tool.JwtUtil;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.RequestMethod;
import com.example.rednote.auth.tool.RedisUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "用户管理", description = "用户相关API")
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {
    // 注入 UserService
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RedisUtil redisUtil;  
    private final PermissionRoleService permissionRoleService;
    private final SerializaUtil serializaUtil;
    @Value("${spring.redis.userTokenSetHeader}")
    private String userTokenSetHeader;
    @Value("${jwt.expiration}")
    private long jwtExpiration;
    @Value("${spring.redis.expiration}")
    private long redisExpiration;

    @Value("${jwt.loginHeader}")
    private String loginHeader;
    @Value("${spring.redis.allowedOnlineNum}")
    private long allowedOnlineNum;

    /*
     * Admin 注册接口
     */
    @Operation(summary = "Admin注册接口",description = "只有Admin账户才能访问")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register/admin")
    public RespondMessage<String> registerAdmin(@RequestBody @Valid RegisterUserDto registerUserDto) {
        if (userService.existsByEmail(registerUserDto.getEmail()) || userService.existsByUsername(registerUserDto.getUsername())) {
             return RespondMessage.fail("用户名或者邮箱已存在");
        }
        String encodedPassword = passwordEncoder.encode(registerUserDto.getPassword());
        registerUserDto.setPassword(encodedPassword);
        User user = userService.registerUserWithRoles(registerUserDto.toUser(), "ROLE_ADMIN");
        // 注册为超级管理员
        log.info("注册管理员用户: {},用户id: {}", user.getUsername(), user.getId());
        return RespondMessage.success("注册成功");
    }
    /*
     * 普通用户注册接口
     */
    @Operation(summary = "普通用户注册接口",description = "注册为普通用户")
    @PostMapping("/register")
    public RespondMessage<String> registerUser(@RequestBody @Valid RegisterUserDto registerUserDto) {
        if (userService.existsByEmail(registerUserDto.getEmail()) || userService.existsByUsername(registerUserDto.getUsername())) {
             return RespondMessage.fail("用户名或者邮箱已存在");
        }
        String encodedPassword = passwordEncoder.encode(registerUserDto.getPassword());
        registerUserDto.setPassword(encodedPassword);
        //注册为普通用户
        User user=userService.registerUserWithRoles(registerUserDto.toUser(),"ROLE_USER");
        log.info("注册用户: {},用户id: {}", user.getUsername(), user.getId());
        return RespondMessage.success("注册成功");
    }
    /*
     * 登录接口
     */
    @PostMapping("/login")
    public RespondMessage<UserDto> loginPage(@RequestBody @Valid LoginUserDto loginUserDto) throws JsonProcessingException {
        // 验证用户名密码是否正确
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginUserDto.getUsername(), loginUserDto.getPassword())
        );
        // 验证通过获取用户实体生成token
        if (!authentication.isAuthenticated()) {
            log.warn("用户登录失败: {}", loginUserDto.getUsername());
            return RespondMessage.fail("用户名或密码错误");
        }
            // 获取用户实体
        UserDto userDto = userService.findByUsername(loginUserDto.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("用户不存在"))
            .toUserDto();
        // 检查同一账户同时在线人数
        if(redisUtil.getLiveTokenSize(userDto.getId())>=allowedOnlineNum){
            log.warn("超过同时在线人数限制: {}", loginUserDto.getUsername());
            return RespondMessage.fail("超过同时在线人数限制");
        }

        // 根据roles获取permissons
        List<String> permissions=permissionRoleService.getPermissionByRoles(userDto.getRoles());
        // 新建JwtUser定义jwt需要保存的字段
        JwtUserDto jwtUserDto=new JwtUserDto();
        jwtUserDto.setPermission(permissions);
        jwtUserDto.setId(userDto.getId());
        jwtUserDto.setRoles(userDto.getRoles());
        jwtUserDto.setUsername(userDto.getUsername());
        // 设置返回信息
        userDto.setPermission(permissions);
        // 生成jti
        String jti=UUID.randomUUID().toString();
        // 生成token并设置到返回信息中
        String token = jwtUtil.generateToken(jti, jwtUserDto, jwtExpiration);
        userDto.setToken(token);
        // 保存到redis中
        redisUtil.set(loginHeader+jti, serializaUtil.toJson(userDto), redisExpiration, TimeUnit.SECONDS);
        redisUtil.setToSet(userTokenSetHeader+userDto.getId(), jti, redisExpiration, TimeUnit.SECONDS);
        log.info("用户登录成功: userId={}, username={}, roles={}", userDto.getId(), userDto.getUsername(), userDto.getRoles());
        return RespondMessage.success("登录成功",userDto);
        }

    /*
     * 错误接口，未被捕获的错误会重定向到这个接口
     */
    @RequestMapping(value = "/error", method = {RequestMethod.GET, RequestMethod.POST})
    public RespondMessage<String> errorRequest() {
        log.warn("请求错误");
        return RespondMessage.fail("不接受的请求类型",404);
    }


}
