package com.example.rednote.auth.security.service.impl;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.rednote.auth.model.user.entity.User;
import com.example.rednote.auth.model.user.service.UserService;
import com.example.rednote.auth.security.service.MyUserDetailsService;
import com.example.rednote.auth.security.service.PermissionRoleService;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class MyUserDetailsServiceImpl implements MyUserDetailsService  {
    private final UserService userService;
    private final PermissionRoleService permissionRoleService;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {


        User user = userService.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        List<String> permissions=permissionRoleService.getPermissionByRoles(user.getRoles());
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(permissions.toArray(new String[0]))
                .build();
    }
    
}
