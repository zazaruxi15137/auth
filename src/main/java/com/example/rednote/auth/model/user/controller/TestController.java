package com.example.rednote.auth.model.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import com.example.rednote.auth.common.RespondMessage;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Tag(name = "Error", description = "错误重定向")
@RestController
@RequestMapping("/api")
public class TestController {

    // @Operation(summary = "角色权限校验", description = "验证用户是否拥有ROLE_USER权限")
    // @PreAuthorize("hasRole('USER')")
    // @PostMapping("/user")
    // public String test() {
    //     return "User角色 权限验证通过";
    // }

    // @Operation(summary = "颗粒权限校验", description = "验证用户是否拥有任意'sys:profile:edit','sys:user:add'权限")
    // @PreAuthorize("hasAnyAuthority('sys:profile:edit','sys:user:add')")
    // @PostMapping("/any")
    // public String testedit() {
    //     return "sys:profile:edit sys:user:add 任意权限验证通过";
    // }

    // @Operation(summary = "颗粒权限校验", description = "验证用户是否拥有全部'sys:profile:edit','sys:user:add'权限")
    // @PreAuthorize("hasAuthority('sys:profile:edit') and hasAuthority('sys:user:add')")
    // @PostMapping("/all")
    // public String test3() {
    //     return "sys:profile:edit sys:user:add 全权限验证通过";
    // }

    // @Operation(summary = "颗粒权限校验", description = "验证用户是否拥有'sys:profile:edit'权限")
    // @PreAuthorize("hasAuthority('sys:profile:edit')")
    // @PostMapping("/edit")
    // public String test2() {
    //     return "sys:profile:edit 权限验证通过";
    // }


    // @Operation(summary = "角色权限校验", description = "验证用户是否拥有ROLE_ADMIN权限")
    // @PreAuthorize("hasRole('ADMIN')")
    // @GetMapping("/admin")
    // public RespondMessage<String> admianTest() {
    //     return RespondMessage.success("Admin 权限验证通过");
    // }

    /*
     * 错误接口，未被捕获的错误会重定向到这个接口
     */
    @RequestMapping(value = "/error", method = {RequestMethod.GET, RequestMethod.POST})
    public RespondMessage<String> errorRequest() {
        log.warn("请求错误");
        return RespondMessage.fail("unsupport request:不接受的请求类型",404);
    }
}
