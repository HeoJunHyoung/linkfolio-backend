package com.example.userservice.service;

import com.example.userservice.dto.*;
import com.example.userservice.dto.event.UserRegistrationRequestedEvent;
import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.entity.UserProfileEntity;
import com.example.userservice.entity.enumerate.UserProfileStatus;
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
    public void createUserProfile(UserRegistrationRequestedEvent event) {
        // 1. 멱등성 보장 (이미 처리된 이벤트인지 확인)
        // (참고: DB 제약조건(PK)에 의해 중복 저장은 막히지만, SAGA 응답을 위해 명시적으로 확인)
        if (userRepository.existsById(event.getUserId())) {
            log.warn("이미 존재하는 UserId로 프로필 생성을 시도했습니다 (멱등성): {}", event.getUserId());
            // 이미 성공한 것으로 간주하고, Exception을 발생시키지 않는다.
            // (EventHandler가 Success 이벤트를 다시 발행할 것임)
            return;
        }

        // 2. DTO -> Entity 변환 (이때 Status는 PENDING)
        UserProfileEntity userProfile = UserProfileEntity.fromEvent(event);

        // 3. 상태를 COMPLETED로 변경
        // (이 서비스에서는 프로필 저장 외 다른 로직이 없으므로 즉시 COMPLETED로 저장)
        userProfile.updateStatus(UserProfileStatus.COMPLETED);

        // 4. DB 저장 (이 과정에서 DB 제약조건 위반 등 예외 발생 가능 -> EventHandler가 catch)
        userRepository.save(userProfile);

        log.info("UserProfile 생성 및 COMPLETED 상태로 저장 성공. UserId: {}", event.getUserId());
    }
}
