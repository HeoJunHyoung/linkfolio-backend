package com.example.userservice.controller;

import com.example.userservice.dto.UserLoginRequest;
import com.example.userservice.dto.UserSignUpRequest;
import com.example.userservice.entity.UserEntity;
import com.example.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class UserController {

    private final UserService userService;

    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome to LinkFolio Project";
    }

    // 회원가입
    @PostMapping("/users/signup")
    public ResponseEntity<Void> signUpApi(@RequestBody UserSignUpRequest request) {
        userService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 로그인
    @PostMapping("/login")
    public void login(@RequestBody UserLoginRequest request) {
        // 로그인 처리는 AuthenticationFilter 위임
    }
    
    // 회원 단일 조회
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserEntity> getUserApi(@PathVariable("userId") Long userId) {
        UserEntity findUser = userService.getUser(userId);
        return ResponseEntity.status(HttpStatus.OK).body(findUser);
    }


}
