package com.example.rednote.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.rednote.auth.entity.Role;

import io.lettuce.core.dynamic.annotation.Param;

@Repository
public interface RoleRepository extends JpaRepository<Role,Long>{

    @Query("select r.id from Role r where r.name in :roleNames")
    List<Long> findIdsByNames(@Param("roleNames") List<String> roleNames);
}
