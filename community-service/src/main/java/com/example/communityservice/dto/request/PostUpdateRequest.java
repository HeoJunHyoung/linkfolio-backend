package com.example.communityservice.dto.request;

import lombok.Data;

@Data
public class PostUpdateRequest {
    private String title;
    private String content;
}