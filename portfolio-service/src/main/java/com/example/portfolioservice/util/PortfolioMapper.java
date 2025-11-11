package com.example.portfolioservice.util;

import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.dto.response.PortfolioResponse;
import com.example.portfolioservice.entity.PortfolioEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    // Entity -> 상세 응답 DTO
    PortfolioResponse toPortfolioResponse(PortfolioEntity entity);

    // Entity -> 카드 응답 DTO
    @Mapping(source = "birthdate", target = "age", qualifiedByName = "birthdateToAge")
    PortfolioCardResponse toPortfolioCardResponse(PortfolioEntity entity);

    //"yyyy-MM-dd" 형식의 생년월일 문자열을 만나이(String)로 변환
    @Named("birthdateToAge")
    default String birthdateToAge(String birthdate) {
        if (birthdate == null || birthdate.isEmpty()) {
            return null;
        }
        try {
            LocalDate birthDate = LocalDate.parse(birthdate, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate today = LocalDate.now();
            int age = Period.between(birthDate, today).getYears();
            return String.valueOf(age);
        } catch (Exception e) {
            return null;
        }
    }
}