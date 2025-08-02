package com.example.rednote.auth.controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.rednote.auth.service.UserService;
import com.example.rednote.auth.tool.RespondMessage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.example.rednote.auth.dto.LoginUserDto;
import com.example.rednote.auth.dto.RegisterUserDto;
import com.example.rednote.auth.dto.UserDto;
import com.example.rednote.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import com.example.rednote.auth.tool.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.RequestMethod;
import com.example.rednote.auth.tool.RedisUtil;


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
    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register/admin")
    public RespondMessage<UserDto> registerAdmin(@RequestBody @Valid RegisterUserDto registerUserDto) {
        String encodedPassword = passwordEncoder.encode(registerUserDto.getPassword());
        registerUserDto.setPassword(encodedPassword);
        User user = userService.registerUserWithRoles(registerUserDto.toUser(), "ADMIN");

        log.info("注册管理员用户: {},用户id: {}", user.getUsername(), user.getId());
        return RespondMessage.success("注册成功", user.toUserDto());
    }

    @PostMapping("/register")
    public RespondMessage<UserDto> registerUser(@RequestBody @Valid RegisterUserDto registerUserDto) {
        String encodedPassword = passwordEncoder.encode(registerUserDto.getPassword());
        registerUserDto.setPassword(encodedPassword);
        User user=userService.registerUser(registerUserDto.toUser());
        log.info("注册用户: {},用户id: {}", user.getUsername(), user.getId());
        return RespondMessage.success("注册成功", user.toUserDto());
    }

    @PostMapping("/login")
    public RespondMessage<UserDto> loginPage(@RequestBody @Valid LoginUserDto loginUserDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginUserDto.getUsername(), loginUserDto.getPassword())
        );

        if (authentication.isAuthenticated()&& redisUtil.hasKey(loginUserDto.getUsername())) {
            log.info("用户登录成功: {}", loginUserDto.getUsername());
            UserDto userDto=userService.findByUsername(loginUserDto.getUsername()).get().toUserDto();
            userDto.setToken(jwtUtil.generateToken(userDto.getUsername(), jwtExpiration));
            return RespondMessage.success("登录成功",userDto );
            
        } else {
            log.warn("用户登录失败: {}", loginUserDto.getUsername());
            return RespondMessage.fail("登录失败");
        }
    }
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/test")
    public String test() {
        return "User 权限验证通过";
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/logout")
    public void logout() {
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/home")
    public RespondMessage<String> admianTest() {
        return RespondMessage.success("Admin 权限验证通过");
    }

    @RequestMapping(value = "/error", method = {RequestMethod.GET, RequestMethod.POST})
    public RespondMessage<String> errorRequest() {
        log.warn("请求错误");
        return RespondMessage.fail("不接受的请求类型",404);
    }
}
