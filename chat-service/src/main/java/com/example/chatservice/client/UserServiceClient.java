package com.example.chatservice.client;

import com.example.chatservice.dto.InternalUserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// name은 Feign이 사용할 이름, url은 ConfigMap의 APP_FEIGN_USER_SERVICE_URL에 정의된 URL입니다.
@FeignClient(name = "user-service", url = "${app.feign.user-service-url}")
public interface UserServiceClient {

    // user-service의 내부용 API 호출
    @GetMapping("/internal/users/{userId}")
    InternalUserProfileResponse getInternalUserProfile(@PathVariable("userId") Long userId);
}