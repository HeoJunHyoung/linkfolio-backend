package com.example.portfolioservice.util;

import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.dto.response.PortfolioDetailsResponse;
import com.example.portfolioservice.entity.PortfolioEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    // Entity -> 상세 응답 DTO
    @Mapping(source = "hashtags", target = "hashtags", qualifiedByName = "stringToHashtagList")
    PortfolioDetailsResponse toPortfolioResponse(PortfolioEntity entity);

    // Entity -> 카드 응답 DTO
    @Mapping(source = "hashtags", target = "hashtags", qualifiedByName = "stringToHashtagList")
    PortfolioCardResponse toPortfolioCardResponse(PortfolioEntity entity);

    // 쉼표로 구분된 문자열을 List<String>으로 변환하는 헬퍼 메서드 (재사용)
    @Named("stringToHashtagList")
    default List<String> stringToHashtagList(String hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(hashtags.split(","));
    }
}