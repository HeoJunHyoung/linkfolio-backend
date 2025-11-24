package com.example.communityservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "chat-service", url = "${app.feign.chat-service-url}")
public interface ChatServiceClient {

    @PostMapping("/internal/chat/send")
    void sendMessageInternal(@RequestParam("senderId") Long senderId,
                             @RequestParam("receiverId") Long receiverId,
                             @RequestBody String content);
}