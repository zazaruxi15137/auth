package com.example.rednote.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.rednote.auth.entity.RolePermission;

import io.lettuce.core.dynamic.annotation.Param;

@Repository
public interface PermissionRoleRepositiry extends JpaRepository<RolePermission, Long> {
    @Query("select rp.permission.code from RolePermission rp where rp.role.id in :roleIds")
    List<String> findPermissionCodesByRoleIds(@Param("roleIds") List<Long> roleIds);
}

