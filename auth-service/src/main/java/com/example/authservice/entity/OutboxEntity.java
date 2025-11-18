package com.example.authservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String aggregateType; // 예: "USER"

    @Column(nullable = false)
    private String aggregateId;   // 예: userId

    @Column(nullable = false)
    private String type;          // 예: "UserRegistrationRequestedEvent"

    // 이벤트 데이터를 JSON 문자열로 저장
    @Column(columnDefinition = "longtext", nullable = false)
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}