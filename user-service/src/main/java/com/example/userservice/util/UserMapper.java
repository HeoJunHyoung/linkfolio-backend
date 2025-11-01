package com.example.userservice.util;

import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.entity.UserProfileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // UserEntity -> UserResponse
    @Mapping(source = "userId", target = "id")
    UserResponse toUserResponse(UserProfileEntity userProfileEntity);

}
