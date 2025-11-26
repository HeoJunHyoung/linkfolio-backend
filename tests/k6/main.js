import { createTestUser } from './utils/auth.js';
import { seedPortfolioData, seedCommunityData, seedSupportData } from './utils/seeder.js';
import { portfolioScenario } from './scenarios/portfolio.js';
import { communityReadScenario, communityWriteScenario } from './scenarios/community.js';
import { supportScenario } from './scenarios/support.js';

// 타겟 주소 (Ingress)
const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export const options = {
    // 셋업 단계 타임아웃 (데이터 생성 시간이 길어질 수 있으므로 넉넉히)
    setupTimeout: '300s',

    scenarios: {
        // 1. 포트폴리오 조회 (기존 20 -> 5)
        portfolio_read: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 5 },  // 5명까지 서서히 증가
                { duration: '20s', target: 5 },  // 5명 유지
                { duration: '10s', target: 0 },  // 0명으로 감소
            ],
            exec: 'runPortfolio',
        },
        // 2. 커뮤니티 조회 (기존 15 -> 5)
        community_read: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 5 },
                { duration: '20s', target: 5 },
                { duration: '10s', target: 0 },
            ],
            exec: 'runCommunityRead',
        },
        // 3. 커뮤니티 작성 (기존 10 -> 2) : 쓰기 작업은 부하가 크므로 더 적게 설정
        community_write: {
            executor: 'constant-vus',
            vus: 2,
            duration: '40s',
            exec: 'runCommunityWrite',
        },
        // 4. 고객센터 (기존 5 -> 2)
        support_read: {
            executor: 'constant-vus',
            vus: 2,
            duration: '40s',
            exec: 'runSupport',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% 요청이 500ms 미만이어야 함
    },
};

// [Setup] 테스트 시작 전 1회 실행 - 데이터 깔기
export function setup() {
    // 1. 주인공 유저(테스트 러너) 생성
    const runnerUser = createTestUser(BASE_URL);

    // 2. 배경 데이터 생성 (백도어 풀가동)
    // 로컬 환경 부하를 고려해 개수를 적절히 조절 (300개는 적당함)
    seedPortfolioData(BASE_URL, 100);
    seedCommunityData(BASE_URL, runnerUser.id, 100);
    seedSupportData(BASE_URL);

    return runnerUser;
}

// [Execution] 시나리오별 실행 함수
export function runPortfolio(user) { portfolioScenario(BASE_URL, user); }
export function runCommunityRead(user) { communityReadScenario(BASE_URL, user); }
export function runCommunityWrite(user) { communityWriteScenario(BASE_URL, user); }
export function runSupport() { supportScenario(BASE_URL); }