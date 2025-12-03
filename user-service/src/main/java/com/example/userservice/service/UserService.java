package com.example.userservice.service;

import com.example.commonmodule.dto.event.UserRegistrationRequestedEvent;
import com.example.userservice.client.dto.InternalUserProfileResponse;
import com.example.userservice.dto.request.UserProfileUpdateRequest;
import com.example.userservice.dto.response.UserInfoResponse;
import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.entity.UserProfileEntity;
import com.example.userservice.entity.enumerate.UserProfileStatus;
import com.example.commonmodule.exception.BusinessException;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.example.userservice.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    // 수동 캐싱을 위해 RedisTemplate과 ObjectMapper 주입
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // 회원 단일 조회 (내 정보 조회 / 특정 회원 조회)
    public UserInfoResponse getUser(Long userId) {
        String cacheKey = "user:profile:" + userId;

        // 1. Redis에서 조회
        try {
            Object cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                // Redis에서 가져온 데이터(LinkedHashMap)를 UserInfoResponse 객체로 안전하게 변환
                return objectMapper.convertValue(cachedData, UserInfoResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 중 오류 발생 (DB에서 조회 시도): {}", e.getMessage());
        }

        // 2. 캐시에 없으면 DB 조회
        UserProfileEntity userProfileEntity = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        String genderString = (userProfileEntity.getGender() != null) ? userProfileEntity.getGender().name() : null;

        UserInfoResponse response = UserInfoResponse.of(userProfileEntity.getEmail(), userProfileEntity.getUsername(), userProfileEntity.getName(),
                userProfileEntity.getBirthdate(), genderString);

        // 3. 조회된 데이터를 Redis에 저장 (TTL 1시간)
        try {
            redisTemplate.opsForValue().set(cacheKey, response, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패: {}", e.getMessage());
        }

        return response;
    }

    /**
     * 내 프로필 정보 수정
     * ㄴ DB 저장 시 CDC가 이벤트를 자동 발행.
     */
    @Transactional
    public UserResponse updateUserProfile(Long userId, UserProfileUpdateRequest request) {
        // 1. 프로필 조회
        UserProfileEntity userProfile = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        // 2. 엔티티 정보 업데이트
        userProfile.updateUserProfile(request.getName(), request.getBirthdate(), request.getGender());

        // 3. DB 저장 (이 시점에 CDC가 UPDATE 이벤트를 감지)
        UserProfileEntity updatedProfile = userRepository.save(userProfile);
        log.info("프로필 '수정' 완료. DB 저장 성공. UserId: {}", updatedProfile.getUserId());

        // 4. Redis 캐시 수동 삭제 (데이터 정합성 유지)
        String cacheKey = "user:profile:" + userId;
        try {
            redisTemplate.delete(cacheKey);
            log.info("Redis 캐시 삭제 완료 Key: {}", cacheKey);
        } catch (Exception e) {
            log.error("Redis 캐시 삭제 실패: {}", e.getMessage());
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

    /**
     * Kafka Consumer가 호출할 프로필 생성 메서드
     */
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

        // 4. DB 저장 (이 시점에 CDC가 INSERT 이벤트를 감지)
        UserProfileEntity savedProfile = userRepository.save(userProfile);

        log.info("UserProfile 생성 및 COMPLETED 상태로 저장 성공. UserId: {}", event.getUserId());
        return savedProfile;
    }
}