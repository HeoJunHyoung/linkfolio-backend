package com.example.userservice.util;

import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // UserEntity -> UserDto
    @Mapping(source = "userId", target = "id")
    UserDto toUserDto(UserEntity userEntity);

    // UserEntity -> UserResponse
    @Mapping(source = "userId", target = "id")
    UserResponse toUserResponse(UserEntity userEntity);

}
