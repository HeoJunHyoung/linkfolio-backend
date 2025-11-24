package com.example.chatservice.controller;

import com.example.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/chat")
@RequiredArgsConstructor
public class InternalChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public void sendMessageInternal(@RequestParam("senderId") Long senderId, @RequestParam("receiverId") Long receiverId, @RequestBody String content) {
        chatService.sendInternalMessage(senderId, receiverId, content);
    }
}