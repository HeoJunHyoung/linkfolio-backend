package com.example.portfolioservice.util;

import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.dto.response.PortfolioResponse;
import com.example.portfolioservice.entity.PortfolioEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    // Entity -> 상세 응답 DTO
    PortfolioResponse toPortfolioResponse(PortfolioEntity entity);

    // Entity -> 카드 응답 DTO
    PortfolioCardResponse toPortfolioCardResponse(PortfolioEntity entity);
}