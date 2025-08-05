package com.example.rednote.auth.security.service;

import java.util.List;

public interface PermissionRoleService {
    List<String> getPermissionByRoles(String roles);
}
