package com.example.userservice.util;

import com.example.userservice.client.dto.InternalUserProfileResponse;
import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.entity.UserProfileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // UserEntity -> UserResponse
    @Mapping(source = "userId", target = "id")
    UserResponse toUserResponse(UserProfileEntity userProfileEntity);


    // UserEntity -> InternalUserProfileResponse (FeignClient 반환용)
    // 필드명이 동일(userId, email, name, birthdate, gender)하므로 별도 매핑 불필요
    InternalUserProfileResponse toInternalResponse(UserProfileEntity userProfileEntity);
}
