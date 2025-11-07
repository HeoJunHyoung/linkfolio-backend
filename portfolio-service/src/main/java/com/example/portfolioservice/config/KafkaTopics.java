package com.example.portfolioservice.config;

public interface KafkaTopics {
    // Auth -> User/Portfolio (회원가입 요청)
    String USER_REGISTRATION_REQUESTED = "user-registration-requested";

    // User -> Portfolio (프로필 변경 알림)
    String USER_PROFILE_UPDATED = "user-profile-updated";
}