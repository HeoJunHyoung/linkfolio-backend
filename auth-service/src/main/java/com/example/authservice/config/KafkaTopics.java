package com.example.authservice.config;

public interface KafkaTopics {
    // Auth -> User (프로필 생성 요청)
    String USER_REGISTRATION_REQUESTED = "user-registration-requested";

    // User -> Auth (프로필 생성 성공)
    String USER_PROFILE_CREATED_SUCCESS = "user-profile-created-success";

    // User -> Auth (프로필 생성 실패 - 보상)
    String USER_PROFILE_CREATED_FAILURE = "user-profile-created-failure";

    // User -> Auth (프로필 변경 사항 전파)
    String USER_PROFILE_UPDATED = "user-profile-updated";
}
