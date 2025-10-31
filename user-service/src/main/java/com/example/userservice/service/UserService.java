package com.example.userservice.service;

import com.example.userservice.dto.*;
import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.entity.UserProfileEntity;
import com.example.userservice.exception.BusinessException;
import com.example.userservice.exception.ErrorCode;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;


    // 회원 단일 조회 (내 정보 조회 / 특정 회원 조회)
    public UserResponse getUser(Long userId) {
        UserProfileEntity userProfileEntity = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(userProfileEntity);
    }

    // Kafka Consumer가 호출할 프로필 생성 메서드
    @Transactional
    public void createUserProfile(UserCreatedEvent event) {
        // 이미 처리된 이벤트인지 확인 (멱살 보장)
        if (userRepository.existsById(event.getUserId())) {
            log.warn("이미 존재하는 UserId로 프로필 생성을 시도했습니다: {}", event.getUserId());
            return;
        }

        // DTO -> Entity 변환
        UserProfileEntity userProfile = UserProfileEntity.fromEvent(event);

        // ID를 수동 설정하여 저장
        userRepository.save(userProfile);
    }
}
