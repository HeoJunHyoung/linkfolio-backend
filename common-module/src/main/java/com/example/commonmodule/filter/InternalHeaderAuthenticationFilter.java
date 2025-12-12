package com.example.commonmodule.filter;

import com.example.commonmodule.dto.security.AuthUser;
import com.example.commonmodule.entity.enumerate.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class InternalHeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 게이트웨이가 주입한 헤더 추출
        String userId = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        String roleHeader = request.getHeader("X-User-Role");

        // 2. 세 헤더가 모두 존재하면, 신뢰하고 AuthUser 객체 생성
        if (userId != null && !userId.isEmpty() &&
                email != null && !email.isEmpty() &&
                roleHeader != null && !roleHeader.isEmpty()) {

            try {
                Role role = Role.valueOf(roleHeader); //  Convert string to enum

                // 3. 생성자 대신 정적 팩토리 메서드 사용
                UserDetails userDetails = AuthUser.fromGatewayHeader(Long.parseLong(userId), email, role);

                // 4. SecurityContextHolder에 인증 객체 등록
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                // ID 파싱 실패, Role enum 변환 실패 등 예외 발생 시 Context를 비움
                logger.warn("Failed to process internal headers", e);
                SecurityContextHolder.clearContext();
            }
        }

        // 5. 다음 필터로 진행
        filterChain.doFilter(request, response);
    }
}
