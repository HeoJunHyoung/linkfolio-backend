import { createTestUser } from './utils/auth.js';
import { seedPortfolioData, seedCommunityData, seedSupportData } from './utils/seeder.js';
import { portfolioScenario } from './scenarios/portfolio.js';
import { communityReadScenario, communityWriteScenario } from './scenarios/community.js';
import { supportScenario } from './scenarios/support.js';

// 타겟 주소 (Ingress)
const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export const options = {
    scenarios: {
        // 1. 포트폴리오 조회 (비중 40%)
        portfolio_read: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 20 },
                { duration: '20s', target: 20 },
                { duration: '10s', target: 0 },
            ],
            exec: 'runPortfolio',
        },
        // 2. 커뮤니티 조회 (비중 30%)
        community_read: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 15 },
                { duration: '20s', target: 15 },
                { duration: '10s', target: 0 },
            ],
            exec: 'runCommunityRead',
        },
        // 3. 커뮤니티 작성 (비중 20% - 쓰기 부하)
        community_write: {
            executor: 'constant-vus',
            vus: 10,
            duration: '40s',
            exec: 'runCommunityWrite',
        },
        // 4. 고객센터 (비중 10% - 캐시 테스트)
        support_read: {
            executor: 'constant-vus',
            vus: 5,
            duration: '40s',
            exec: 'runSupport',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% 요청이 500ms 미만
    },
};

// [Setup] 테스트 시작 전 1회 실행 - 데이터 깔기
export function setup() {
    // 1. 주인공 유저(테스트 러너) 생성
    const runnerUser = createTestUser(BASE_URL);

    // 2. 배경 데이터 생성 (백도어 풀가동)
    // - 포트폴리오 50개 생성
    seedPortfolioData(BASE_URL, 50);
    // - 게시글 1000개 생성 (주인공 유저가 작성한 것으로 처리)
    seedCommunityData(BASE_URL, runnerUser.id, 1000);
    // - 공지사항/FAQ 생성
    seedSupportData(BASE_URL);

    return runnerUser;
}

// [Execution] 시나리오별 실행 함수
export function runPortfolio(user) { portfolioScenario(BASE_URL, user); }
export function runCommunityRead(user) { communityReadScenario(BASE_URL, user); }
export function runCommunityWrite(user) { communityWriteScenario(BASE_URL, user); }
export function runSupport() { supportScenario(BASE_URL); }