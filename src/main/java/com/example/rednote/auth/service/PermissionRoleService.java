package com.example.rednote.auth.service;

import java.util.List;




public interface PermissionRoleService {
    List<String> getPermissionByRoles(String roles);
}
