package com.example.userservice.controller;

import com.example.userservice.dto.*;
import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/")
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome to LinkFolio Project";
    }


    // 내 정보 조회
    @GetMapping("/users/me")
    public ResponseEntity<UserResponse> getMyInfoApi(@AuthenticationPrincipal AuthUser authUser) {
        UserResponse userResponse = userService.getUser(authUser.getUserId());
        return ResponseEntity.status(HttpStatus.OK).body(userResponse);
    }

    // 특정 회원 단일 조회
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserResponse> getUserApi(@PathVariable("userId") Long userId) {
        UserResponse userResponse = userService.getUser(userId);
        return ResponseEntity.status(HttpStatus.OK).body(userResponse);
    }

}
