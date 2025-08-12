package com.example.rednote.auth.security.service.impl;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.rednote.auth.security.repository.PermissionRoleRepositiry;
import com.example.rednote.auth.security.repository.RoleRepository;
import com.example.rednote.auth.security.service.PermissionRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionRoleServiceImpl implements PermissionRoleService{

    private final PermissionRoleRepositiry permissionRoleRepositiry;

    private final RoleRepository roleRepository;
    @Value("${role.separator}")
    private String regx;
    

    @Override
    public List<String> getPermissionByRoles(String roles) {
        List<String> roleList=new ArrayList<>(Arrays.asList(roles.split(regx)));

        List<Long> permissionIds= roleRepository.findIdsByRoleNames(roleList);

        List<String> l=permissionRoleRepositiry.findPermissionCodesByRoleIds(permissionIds);

        roleList.addAll(l);

        
        return roleList;
    }
    
}
