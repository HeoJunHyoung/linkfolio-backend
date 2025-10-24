package com.example.userservice.service;

import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.dto.UserSignUpRequest;
import com.example.userservice.entity.UserEntity;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.NicknameGenerator;
import com.example.userservice.util.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final NicknameGenerator nicknameGenerator;

    // 회원가입
    @Transactional
    public void signUp(UserSignUpRequest request) {
        validatePasswordMatch(request);             // 비밀번호 검증
        validateEmailDuplicate(request.getEmail()); // 이메일 검증

        // 객체 생성
        UserEntity signUpUser = UserEntity.of(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                nicknameGenerator.generateUniqueNickname()
        );

        userRepository.save(signUpUser);  // 객체 저장
    }


    // 회원 단일 조회
    public UserResponse getUser(Long userId) {
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));
        return userMapper.toUserResponse(userEntity);
    }


    // =====================
    // 서비스 내부 헬퍼 메서드
    // =====================
    private void validatePasswordMatch(UserSignUpRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
    }

    private void validateEmailDuplicate(String email) {
        if (userRepository.findUserDetailsByEmail(email).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }
    }

    public UserDto getUserDetailsByEmail(String email) {
        UserEntity userEntity = userRepository.findUserDetailsByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원 입니다."));
        
        return userMapper.toUserDto(userEntity); // Mapper 이용
    }


}
