package com.example.chatservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_user_profile") // 로컬 캐시용 컬렉션
public class ChatUserProfileEntity {

    @Id
    private Long userId; // 원본(user-service)의 ID와 동일

    private String name;

}