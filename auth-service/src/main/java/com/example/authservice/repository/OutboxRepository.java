package com.example.authservice.repository;

import com.example.authservice.entity.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {
}