// 백도어 API를 호출하여 데이터를 DB에 대량 저장하는 로직
import http from 'k6/http';
import { check } from 'k6';

// 1. 실제 로그인 가능한 유저 생성 (Auth + User + Portfolio 연쇄 저장)
export function seedRealUsers(baseUrl, startIdx, count) {
    const BATCH_SIZE = 10;
    console.log(`[Seeder] Creating ${count} REAL users (starting from user_${startIdx})...`);

    for (let i = 0; i < count; i += BATCH_SIZE) {
        const currentBatchSize = Math.min(BATCH_SIZE, count - i);
        const authRequests = [];

        for (let j = 0; j < currentBatchSize; j++) {
            const idNum = startIdx + i + j;
            const payload = JSON.stringify({
                email: `user_${idNum}@test.com`,
                username: `user_${idNum}`,
                password: 'testPassword123!',
                name: `Real User ${idNum}`,
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

        const authResponses = http.batch(authRequests);

        // 생성된 유저 ID로 포트폴리오도 생성 (발행 처리)
        const pfRequests = [];
        authResponses.forEach((res) => {
            if (res.status === 200) {
                const userId = res.json();
                pfRequests.push({
                    method: 'POST',
                    url: `${baseUrl}/portfolio-service/portfolio/test/setup`,
                    body: JSON.stringify({ userId: userId }),
                    params: { headers: { 'Content-Type': 'application/json' } }
                });
            }
        });

        if (pfRequests.length > 0) {
            http.batch(pfRequests);
        }
    }
}

// 2. 조회 성능용 가짜 포트폴리오 생성 (Auth 생략, Portfolio DB만 Insert)
export function seedDummyPortfolios(baseUrl, startId, count) {
    const BATCH_SIZE = 30; // 인증 없으니 더 많이 병렬 처리
    console.log(`[Seeder] Creating ${count} DUMMY portfolios (Auth Skipped)...`);

    for (let i = 0; i < count; i += BATCH_SIZE) {
        const requests = [];
        const currentBatchSize = Math.min(BATCH_SIZE, count - i);

        for (let j = 0; j < currentBatchSize; j++) {
            const dummyUserId = startId + i + j;
            requests.push({
                method: 'POST',
                url: `${baseUrl}/portfolio-service/portfolio/test/setup`,
                body: JSON.stringify({ userId: dummyUserId }),
                params: { headers: { 'Content-Type': 'application/json' } }
            });
        }
        http.batch(requests);

        if ((i + BATCH_SIZE) % 1000 === 0) {
            console.log(`... ${i + BATCH_SIZE} dummy portfolios created`);
        }
    }
}

// 3. 커뮤니티 게시글 생성
export function seedCommunityPosts(baseUrl, userId, count) {
    console.log(`[Seeder] Creating ${count} posts...`);
    // 한 번 호출로 N개 만드는 백도어 사용
    const payload = JSON.stringify({ userId: userId, count: count });
    http.post(`${baseUrl}/community-service/community/test/setup`, payload, {
        headers: { 'Content-Type': 'application/json' }
    });
}

// 4. 고객센터 데이터 생성
export function seedSupportData(baseUrl, repeatCount) {
    console.log(`[Seeder] Creating Support data (${repeatCount} batches)...`);
    for (let i = 0; i < repeatCount; i++) {
        http.post(`${baseUrl}/support-service/support/test/setup`, null, {
            headers: { 'Content-Type': 'application/json' }
        });
    }
}