package com.example.rednote.auth.service;

import com.example.rednote.auth.entity.User;
import com.example.rednote.auth.exception.CustomException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.example.rednote.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.example.rednote.auth.tool.RedisUtil;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


@Service
public class UserService implements UserDetailsService{
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RedisUtil redisUtil;
    @Value("${spring.redis.expiration}")
    private long expiration; // Token过期时间，单位为秒
    /**
     * 新用户注册
     * @param user 用户数据
     * @return 注册后的用户实体
     * @throws IllegalArgumentException 如果用户名或邮箱已存在
     */
    public User registerUser(User user) {
        if (existsByEmail(user.getEmail()) || existsByUsername(user.getUsername())) {
            throw new CustomException("用户名或邮箱已存在");
        }
        user.setRoles("USER"); // 默认角色为 USER

        return userRepository.save(user); 
    }

    public User registerUserWithRoles(User user, String roles) {
        if (existsByEmail(user.getEmail()) || existsByUsername(user.getUsername())) {
            throw new CustomException("用户名或邮箱已存在");
        }
        user.setRoles(roles);

        return userRepository.save(user); 
    }

    // /**
    //  * 更新用户信息
    //  * @param user 用户数据
    //  * @return 更新后的用户实体
    //  */
    // public User updateUser(User user) {
    //     if (user.getId() == null || !userRepository.existsById(user.getId())) {
    //         throw new CustomException("用户不存在");
    //     }
    //     User oldUser = userRepository.findById(user.getId()).orElseThrow(() -> new CustomException("用户不存在"));
    //     if (existsByEmail(user.getEmail()) && !oldUser.getId().equals(user.getId())) {
    //         throw new CustomException("邮箱已被其他用户使用");
    //     }
    //     user.setRoles(oldUser.getRoles());
    //     return userRepository.save(user);
    // }

    /**
     * 检查用户ID是否存在
     * @param id 用户ID
     * @return true 如果用户ID存在，否则 false
     */
    public boolean existsById(Long id) {
        return userRepository.existsById(id);
    }

    /**
     * 根据ID查找用户
     * @param id 用户ID
     * @return 用户实体的Optional
     */
    public Optional<User> findById(Long id) {
        if (!existsById(id)) {
            throw new CustomException("错误的用户ID");
        }
        return userRepository.findById(id);
    }



    /**
     * 检查用户名是否存在
     * @param username 用户名
     * @return true 如果用户名存在，否则 false
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 根据邮箱查找用户
     * @param email 用户邮箱
     * @return 用户实体的Optional
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
   
    /**
     * 检查用户名是否存在
     * @param username 用户名
     * @return true 如果用户名存在，否则 false
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户实体的Optional
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 删除用户
     * @param id 用户ID
     */
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        redisUtil.set(user.getUsername(), user.toLoginUserDto(), expiration, TimeUnit.SECONDS);
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRoles())
                .build();
    }
}