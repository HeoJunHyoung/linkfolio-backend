package com.example.userservice.service;

import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserSignUpRequest;
import com.example.userservice.entity.UserEntity;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.NicknameGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // 회원가입
    @Transactional
    public void signUp(UserSignUpRequest request) {
        validatePasswordMatch(request);             // 비밀번호 검증
        validateEmailDuplicate(request.getEmail()); // 이메일 검증
        UserEntity signUpUser = UserEntity.of(request.getEmail(), generateUniqueNickname(), passwordEncoder.encode(request.getPassword()));   // 객체 생성
        userRepository.save(signUpUser);  // 객체 저장
    }

    // 회원 단일 조회
    public UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));
    }


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findUserDetailsByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));

        // 아래의 User 객체는 Spring Security의 고유 객체
        return new User(userEntity.getEmail(), userEntity.getPassword(),
                        true, true, true, true,
                        new ArrayList<>());
    }

    // 서비스 내부 헬퍼 메서드
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

    private String generateUniqueNickname() {
        String nickname;
        do {
            nickname = NicknameGenerator.generate();
        } while (userRepository.existsByNickname(nickname));    // 닉네임 검증
        return nickname;
    }

    public UserDto getUserDetailsByEmail(String email) {
        UserEntity userEntity = userRepository.findUserDetailsByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원 입니다."));
        return UserDto.of(userEntity.getUserId(), userEntity.getEmail(), userEntity.getPassword(), userEntity.getNickname());
    }


}
