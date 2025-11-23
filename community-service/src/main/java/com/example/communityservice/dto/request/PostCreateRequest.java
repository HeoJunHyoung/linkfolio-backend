package com.example.communityservice.dto.request;

import com.example.communityservice.entity.enumerate.PostCategory;
import lombok.Data;

import java.util.List;

@Data
public class PostCreateRequest {

    private PostCategory category;

    private String title;

    private String content;

    private List<String> tags;
}