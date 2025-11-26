package com.example.authservice.controller;

import com.example.authservice.dto.request.UserSignUpRequest;
import com.example.authservice.entity.AuthUserEntity;
import com.example.authservice.entity.OutboxEntity;
import com.example.authservice.entity.enumerate.AuthStatus;
import com.example.authservice.repository.AuthUserRepository;
import com.example.authservice.repository.OutboxRepository;
import com.example.commonmodule.dto.event.UserRegistrationRequestedEvent;
import com.example.commonmodule.entity.enumerate.UserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "Test Setup API", description = "성능 테스트 용도의 백도어 API (배포 환경 사용 금지 및 프론트엔드는 해당 API 무시)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/test")
@Slf4j
//@Profile({"dev", "test"})
public class TestSetupController {

    private final AuthUserRepository authUserRepository;
    private final OutboxRepository outboxRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    /**
     * 유저 강제 생성 (SAGA 우회, 즉시 COMPLETED)
     * ㄴ 이메일 인증 생략
     * ㄴ Auth DB에 COMPLETED 상태로 저장 (로그인 가능)
     * ㄴ User DB 동기화를 위한 Outbox 이벤트 발행
     */
    @Operation(summary = "테스트용 유저 생성", description = "이메일 인증 없이 COMPLETED 상태의 유저를 생성하고 Outbox 이벤트를 발행합니다.")
    @PostMapping("/setup-user")
    public ResponseEntity<Long> setupUser(@RequestBody UserSignUpRequest request) throws Exception {

        // 1. 트랜잭션 내에서 DB 저장 수행
        Long userId = createUserInTransaction(request);

        // 2. CDC 전파 대기 (Sleep)
        // 트랜잭션이 커밋된 직후 Debezium이 이벤트를 가져가 user-service로 전파하기까지 시간이 필요함.
        // ㄴ k6에서 sleep을 줘도 되지만, 확실한 정합성을 위해 여기서 2초 대기 후 응답.
        Thread.sleep(2000);

        log.info("테스트 유저 생성 완료 (Waited 1.5s for CDC). UserId: {}", userId);
        return ResponseEntity.ok(userId);
    }

    @Transactional
    public Long createUserInTransaction(UserSignUpRequest request) throws Exception {
        // 1. AuthUser 생성 (초기 PENDING 상태)
        AuthUserEntity authUser = AuthUserEntity.ofLocal(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getUsername(),
                request.getName()
        );

        // 2. 테스트용이므로 즉시 COMPLETED로 상태 변경 (로그인 허용)
        authUser.updateStatus(AuthStatus.COMPLETED);

        AuthUserEntity savedUser = authUserRepository.save(authUser);

        // 3. Outbox 이벤트 저장 (User-Service 프로필 생성 트리거)
        // 원래 SAGA에서는 PENDING 상태일 때 발행하지만, 여기서는 User DB 생성을 위해 동일하게 발행
        UserRegistrationRequestedEvent event = UserRegistrationRequestedEvent.builder()
                .userId(savedUser.getUserId())
                .email(request.getEmail())
                .username(request.getUsername())
                .name(request.getName())
                .birthdate(request.getBirthdate())
                .gender(request.getGender())
                .provider(UserProvider.LOCAL.name())
                .role(savedUser.getRole())
                .build();

        String payload = objectMapper.writeValueAsString(event);

        OutboxEntity outboxEvent = OutboxEntity.builder()
                .aggregateType("USER")
                .aggregateId(savedUser.getUserId().toString())
                .type("UserRegistrationRequestedEvent")
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .build();

        outboxRepository.save(outboxEvent);

        return savedUser.getUserId();
    }


}
