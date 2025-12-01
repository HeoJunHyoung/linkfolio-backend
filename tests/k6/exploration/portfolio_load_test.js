import { sleep } from 'k6';
import { loginAndGetHeader } from '../utils/auth.js';
import { generateSummary } from '../utils/reporter.js';
import { portfolioList, portfolioDetail, portfolioLike } from '../workloads/portfolio.js';

export const options = {
    scenarios: {
        // [시나리오 1] 포트폴리오 탐색 (검색 조건 변경)
        exploration_scenario: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },
                { duration: '2m', target: 40 },
                { duration: '30s', target: 0 },
            ],
            exec: 'runExploration',
        },

        // [시나리오 2] 좋아요 테러 (동시성 테스트)
        like_scenario: {
            executor: 'constant-vus',
            vus: 10,
            duration: '3m',
            exec: 'runLike',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

export function setup() {
    return loginAndGetHeader('http://linkfolio.127.0.0.1.nip.io', 'user_1', 'testPassword123!');
}

export function runExploration(headers) {
    portfolioList(headers); // 인기순, 최신순 등 정렬
    sleep(1);
    portfolioDetail(headers);
    sleep(2);
}

export function runLike(headers) {
    portfolioLike(headers); // 좋아요 토글 (Insert/Delete 반복)
    sleep(0.5); // 빠르게 반복
}

export function handleSummary(data) {
    return generateSummary(data, "exploration/portfolio_focus_test");
}