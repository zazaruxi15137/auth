package com.example.rednote.auth.model.user.service;

import java.util.Optional;

import com.example.rednote.auth.model.user.entity.User;



public interface UserService{

    /**
     * 新用户注册
     * @param user 用户数据
     * @return 注册后的用户实体
     * @throws IllegalArgumentException 如果用户名或邮箱已存在
     */
    public User registerUser(User user);

    public User registerUserWithRoles(User user, String roles);
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
    public boolean existsById(Long id);

    /**
     * 根据ID查找用户
     * @param id 用户ID
     * @return 用户实体的Optional
     */
    public Optional<User> findById(Long id);



    /**
     * 检查用户名是否存在
     * @param username 用户名
     * @return true 如果用户名存在，否则 false
     */
    public boolean existsByEmail(String email);

    /**
     * 根据邮箱查找用户
     * @param email 用户邮箱
     * @return 用户实体的Optional
     */
    public Optional<User> findByEmail(String email);
   
    /**
     * 检查用户名是否存在
     * @param username 用户名
     * @return true 如果用户名存在，否则 false
     */
    public boolean existsByUsername(String username);

    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户实体的Optional
     */
    public Optional<User> findByUsername(String username);

    /**
     * 删除用户
     * @param id 用户ID
     */
    public void deleteUser(Long id);

 
}