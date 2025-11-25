package com.example.communityservice.dto.request;

import com.example.communityservice.entity.enumerate.RecruitmentStatus;
import lombok.Data;

@Data
public class RecruitmentStatusRequest {
    private RecruitmentStatus status;
}