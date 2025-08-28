package com.example.gateway_service.gateway_service.security.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.gateway_service.gateway_service.security.model.Role;

import io.lettuce.core.dynamic.annotation.Param;

@Repository
public interface RoleRepository extends JpaRepository<Role,Long>{

    @Query("select r.id from Role r where r.roleName  in :roleNames")
    List<Long> findIdsByRoleNames(@Param("roleNames") List<String> roleNames);
}
