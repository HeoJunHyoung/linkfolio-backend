package com.example.portfolioservice.client;

import com.example.portfolioservice.client.dto.InternalUserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


// name: user-service의 spring.application.name 또는 k8s 서비스 이름
// url: application.yml의 app.feign.user-service-url 값
@FeignClient(name = "user-service", url = "${app.feign.user-service-url}")
public interface UserServiceClient {

    // FeignClient는 (Context-Path + Controller 경로)를 모두 포함해야 함
    @GetMapping("/user-service/internal/users/{userId}")
    InternalUserProfileResponse getUserProfile(@PathVariable("userId") Long userId);
}