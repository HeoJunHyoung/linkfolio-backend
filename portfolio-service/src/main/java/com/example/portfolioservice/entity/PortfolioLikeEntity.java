package com.example.portfolioservice.entity;

import com.example.commonmodule.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 한 사용자가 한 포트폴리오에 대해 중복으로 '관심'을 누를 수 없도록 유니크 제약조건 설정
@Table(name = "portfolio_like", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_user_portfolio",
                columnNames = {"user_id", "portfolio_id"}
        )
})
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioLikeEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // 관심을 누른 사용자의 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private PortfolioEntity portfolio;

    // 생성자
    private PortfolioLikeEntity(Long userId, PortfolioEntity portfolio) {
        this.userId = userId;
        this.portfolio = portfolio;
    }

    // 정적 팩토리 메서드
    public static PortfolioLikeEntity of(Long userId, PortfolioEntity portfolio) {
        return new PortfolioLikeEntity(userId, portfolio);
    }
}