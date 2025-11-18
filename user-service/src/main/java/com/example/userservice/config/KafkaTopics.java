package com.example.userservice.config;

public interface KafkaTopics {
    // Auth -> User (프로필 생성 요청)
    String USER_REGISTRATION_REQUESTED = "outbox.event.UserRegistrationRequestedEvent";

    // User -> Auth (프로필 생성 성공)
    String USER_PROFILE_CREATED_SUCCESS = "user-profile-created-success";

    // User -> Auth (프로필 생성 실패 - 보상)
    String USER_PROFILE_CREATED_FAILURE = "user-profile-created-failure";

    // '프로필이 생성/변경되었음'을 모든 서비스에 전파
    String USER_PROFILE_UPDATED = "user-profile-updated";
}
