import http from 'k6/http';
import { createTestUser } from './auth.js';

// 1. 포트폴리오 조회용 배경 데이터 생성 (유저+포트폴리오 N개)
export function seedPortfolioData(baseUrl, count = 50) {
    console.log(`[Seeder] Creating ${count} portfolios...`);
    for (let i = 0; i < count; i++) {
        const user = createTestUser(baseUrl); // Auth 백도어 (ID 반환)

        // Portfolio 백도어 호출 (즉시 공개 상태 생성)
        const pfPayload = JSON.stringify({ userId: user.id });
        http.post(`${baseUrl}/portfolio-service/portfolio/test/setup`, pfPayload, {
            headers: { 'Content-Type': 'application/json' }
        });
    }
}

// 2. 커뮤니티 조회용 게시글 대량 생성 (작성자 1명이 N개 작성)
export function seedCommunityData(baseUrl, userId, count = 1000) {
    console.log(`[Seeder] Creating ${count} posts for userId ${userId}...`);
    const payload = JSON.stringify({
        userId: userId,
        count: count
    });
    http.post(`${baseUrl}/community-service/community/test/setup`, payload, {
        headers: { 'Content-Type': 'application/json' }
    });
}

// 3. 고객센터 데이터 생성 (공지/FAQ)
export function seedSupportData(baseUrl) {
    console.log(`[Seeder] Creating Support data (Notice/FAQ)...`);
    http.post(`${baseUrl}/support-service/support/test/setup`, null, {
        headers: { 'Content-Type': 'application/json' }
    });
}