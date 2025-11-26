import http from 'k6/http';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// [변경] 병렬 처리(Batch)를 적용한 포트폴리오 데이터 생성 함수
export function seedPortfolioData(baseUrl, count = 50) {
    // 한 번에 동시에 보낼 요청 수
    const BATCH_SIZE = 10;

    console.log(`[Seeder] Creating ${count} portfolios using batch processing...`);

    for (let i = 0; i < count; i += BATCH_SIZE) {
        const currentBatchSize = Math.min(BATCH_SIZE, count - i);
        const authRequests = [];

        // 1. [병렬 준비] 회원가입 요청 객체 생성
        for (let j = 0; j < currentBatchSize; j++) {
            const uniqueId = randomString(8);
            const payload = JSON.stringify({
                email: `test_${uniqueId}_${i+j}@linkfolio.com`,
                username: `user_${uniqueId}_${i+j}`,
                password: 'testPassword123!',
                name: `K6 User ${i+j}`,
                birthdate: '1990-01-01',
                gender: 'MALE'
            });

            authRequests.push({
                method: 'POST',
                url: `${baseUrl}/auth-service/auth/test/setup-user`,
                body: payload,
                params: { headers: { 'Content-Type': 'application/json' } }
            });
        }

        // 2. [병렬 실행] Auth 요청 동시 전송 (여기서 50개가 동시에 2초 대기하므로 총 2초 소요)
        const authResponses = http.batch(authRequests);

        // 3. [병렬 준비] 생성된 ID로 포트폴리오 생성 요청 객체 준비
        const portfolioRequests = [];
        authResponses.forEach((res) => {
            if (res.status === 200) {
                const userId = res.json(); // 응답받은 ID
                const pfPayload = JSON.stringify({ userId: userId });

                portfolioRequests.push({
                    method: 'POST',
                    url: `${baseUrl}/portfolio-service/portfolio/test/setup`,
                    body: pfPayload,
                    params: { headers: { 'Content-Type': 'application/json' } }
                });
            } else {
                console.error(`Auth setup failed: ${res.status} ${res.body}`);
            }
        });

        // 4. [병렬 실행] 포트폴리오 생성 요청 동시 전송
        if (portfolioRequests.length > 0) {
            http.batch(portfolioRequests);
        }

        console.log(`[Seeder] Progress: ${Math.min(i + BATCH_SIZE, count)}/${count} done.`);

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