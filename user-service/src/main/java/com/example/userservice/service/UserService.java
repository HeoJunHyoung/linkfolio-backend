package com.example.userservice.service;

import com.example.userservice.dto.*;
import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.entity.UserProfileEntity;
import com.example.userservice.entity.UserProvider;
import com.example.userservice.exception.BusinessException;
import com.example.userservice.exception.ErrorCode;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;


    // 회원 단일 조회 (내 정보 조회 / 특정 회원 조회)
    public UserResponse getUser(Long userId) {
        UserProfileEntity userProfileEntity = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(userProfileEntity);
    }

}
