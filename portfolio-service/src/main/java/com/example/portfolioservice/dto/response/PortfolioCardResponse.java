package com.example.portfolioservice.dto.response;

import com.example.commonmodule.entity.enumerate.Gender;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioCardResponse {
    private Long userId;
    private String name;
    private String birthdate;
    private Gender gender;
    private String photoUrl;
    private String oneLiner; // 한 마디
}