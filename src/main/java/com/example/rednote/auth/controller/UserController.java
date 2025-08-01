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
import com.example.rednote.auth.dto.UserDto;
import com.example.rednote.auth.entity.User;
import com.example.rednote.auth.tool.UserParaphrase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.rednote.auth.tool.JwtUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMethod;






@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {
    // 注入 UserService
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;


    @PostMapping("/register")
    public RespondMessage<UserDto> registerUser(@RequestBody UserDto userDto) {
        String encodedPassword = passwordEncoder.encode(userDto.getPassword());
        userDto.setPassword(encodedPassword);
        User user=userService.registerUser(UserParaphrase.paraphraseToUser(userDto));
        return RespondMessage.success("注册成功", UserParaphrase.paraphraseToUserDto(user, true));
    }

    @PostMapping("/login")
    public RespondMessage<String> loginPage(@RequestParam String username, @RequestParam String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
        if (authentication.isAuthenticated()) {
            return RespondMessage.success("登录成功", jwtUtil.generateToken(username));
        } else {
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
        return RespondMessage.fail("请求错误");
    }
    
    
    

}
