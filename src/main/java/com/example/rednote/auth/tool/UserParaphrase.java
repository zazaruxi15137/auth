package com.example.rednote.auth.tool;


import com.example.rednote.auth.dto.UserDto;
import com.example.rednote.auth.entity.User;
public class UserParaphrase{


    public static User paraphraseToUser(UserDto userDto) {
        User user = new User();
        user.setId(userDto.getId());
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setPassword(userDto.getPassword());
        return user;    
    }

    public static UserDto paraphraseToUserDto(User user, Boolean mask) {
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setUsername(user.getUsername());
        userDto.setEmail(user.getEmail());
        if (mask) {
            userDto.setPassword("**hidden**");
        } else {
            userDto.setPassword(user.getPassword());
        }
        return userDto;
    
} 
}  
