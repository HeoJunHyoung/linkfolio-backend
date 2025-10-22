package com.example.userservice.filter;

import com.example.userservice.dto.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 게이트웨이 헤더(X-User-Id, X-User-Email)를 신뢰하여 DB 조회 없이
 * AuthUser 객체를 생성하고 SecurityContext에 인증 객체를 등록
 */
public class InternalHeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 게이트웨이가 주입한 헤더 추출
        String userId = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");

        // 2. 두 헤더가 모두 존재하면, 신뢰하고 AuthUser 객체 생성
        if (userId != null && !userId.isEmpty() && email != null && !email.isEmpty()) {
            try {
                // 3. DB 조회 없이 헤더 정보로 UserDetails(AuthUser) 생성
                UserDetails userDetails = new AuthUser(
                        Long.parseLong(userId),
                        email
                );

                // 4. SecurityContextHolder에 인증 객체 등록
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                // ID 파싱 실패 등 예외 발생 시 Context를 비움
                logger.warn("Failed to process internal headers", e);
                SecurityContextHolder.clearContext();
            }
        }

        // 5. 다음 필터로 진행
        filterChain.doFilter(request, response);
    }
}