package com.example.userservice.service;

import com.example.userservice.client.dto.InternalUserProfileResponse;
import com.example.userservice.config.KafkaTopics;
import com.example.userservice.dto.event.UserProfilePublishedEvent;
import com.example.userservice.dto.event.UserRegistrationRequestedEvent;
import com.example.userservice.dto.request.UserProfileUpdateRequest;
import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.entity.UserProfileEntity;
import com.example.userservice.entity.enumerate.UserProfileStatus;
import com.example.commonmodule.exception.BusinessException;
import com.example.commonmodule.exception.ErrorCode;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.userservice.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;


    // 회원 단일 조회 (내 정보 조회 / 특정 회원 조회)
    public UserResponse getUser(Long userId) {
        UserProfileEntity userProfileEntity = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
        return userMapper.toUserResponse(userProfileEntity);
    }

    /**
     * 내 프로필 정보 수정
     */
    @Transactional
    public UserResponse updateUserProfile(Long userId, UserProfileUpdateRequest request) {
        // 1. 프로필 조회
        UserProfileEntity userProfile = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        // 2. 엔티티 정보 업데이트
        userProfile.updateUserProfile(
                request.getName(),
                request.getBirthdate(),
                request.getGender()
        );

        // 3. DB 저장 (JPA Dirty-Checking으로 생략 가능하나 명시)
        UserProfileEntity updatedProfile = userRepository.save(userProfile);

        // 4. Kafka 이벤트 발행 (데이터 동기화)
        //    (portfolio-service 등이 이 이벤트를 수신하여 캐시를 갱신)
        try {
            UserProfilePublishedEvent event = UserProfilePublishedEvent.fromEntity(updatedProfile);
            kafkaTemplate.send(KafkaTopics.USER_PROFILE_UPDATED, event);
            log.info("프로필 '수정' 완료. 데이터 전파(Fan-out) 이벤트 발행. UserId: {}", updatedProfile.getUserId());
        } catch (Exception e) {
            log.error("프로필 수정은 완료했으나 Kafka 이벤트 발행 실패, 롤백 필요. UserId: {}", userId, e);
            // @Transactional에 의해 런타임 예외 발생 시 DB 롤백
            throw new BusinessException(INTERNAL_SERVER_ERROR);
        }

        // 5. DTO로 변환하여 반환
        return userMapper.toUserResponse(updatedProfile);
    }

    // FeignClient 호출을 위한 내부 프로필 조회
    public InternalUserProfileResponse getInternalUserProfile(Long userId) {
        UserProfileEntity userProfileEntity = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
        return userMapper.toInternalResponse(userProfileEntity);
    }

    // Kafka Consumer가 호출할 프로필 생성 메서드
    @Transactional
    public UserProfileEntity createUserProfile(UserRegistrationRequestedEvent event) {
        // 1. 멱등성 보장 (이미 처리된 이벤트인지 확인)
        if (userRepository.existsById(event.getUserId())) {
            log.warn("이미 존재하는 UserId로 프로필 생성을 시도했습니다 (멱등성): {}", event.getUserId());
            // 이미 생성된 엔티티를 반환
            return userRepository.findById(event.getUserId())
                    .orElseThrow(() -> new BusinessException(INTERNAL_SERVER_ERROR));
        }

        // 2. DTO -> Entity 변환 (이때 Status는 PENDING)
        UserProfileEntity userProfile = UserProfileEntity.fromEvent(event);

        // 3. 상태를 COMPLETED로 변경
        userProfile.updateStatus(UserProfileStatus.COMPLETED);

        // 4. DB 저장
        UserProfileEntity savedProfile = userRepository.save(userProfile);

        log.info("UserProfile 생성 및 COMPLETED 상태로 저장 성공. UserId: {}", event.getUserId());
        return savedProfile;
    }
}
