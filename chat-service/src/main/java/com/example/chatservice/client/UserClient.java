package com.example.chatservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// K8s Service Name: user-service
@FeignClient(name = "user-service", url = "${app.feign.user-service-url}")
public interface UserClient {

    @GetMapping("/internal/users/{userId}")
    InternalUserProfileResponse getInternalUserProfile(@PathVariable("userId") Long userId);
}