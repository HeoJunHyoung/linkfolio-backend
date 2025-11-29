package com.example.portfolioservice.util;

import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.dto.response.PortfolioDetailsResponse;
import com.example.portfolioservice.entity.PortfolioEntity;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    /**
     * Entity -> 상세 응답 DTO 변환
     */
    @Mapping(source = "entity.hashtags", target = "hashtags", qualifiedByName = "stringToHashtagList")
    @Mapping(target = "isLiked", source = "isLiked") // 파라미터로 받은 isLiked 매핑
    @Mapping(target = "isPublished", source = "entity.published") // Lombok getter(isPublished) -> property(published)
    PortfolioDetailsResponse toPortfolioResponse(PortfolioEntity entity, boolean isLiked);

    // 쉼표로 구분된 문자열을 List<String>으로 변환하는 헬퍼 메서드
    @Named("stringToHashtagList")
    default List<String> stringToHashtagList(String hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(hashtags.split(","));
    }
}