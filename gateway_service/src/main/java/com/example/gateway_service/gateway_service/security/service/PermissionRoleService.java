package com.example.gateway_service.gateway_service.security.service;
import java.util.List;

public interface PermissionRoleService {
    List<String> getPermissionByRoles(String roles);
}
