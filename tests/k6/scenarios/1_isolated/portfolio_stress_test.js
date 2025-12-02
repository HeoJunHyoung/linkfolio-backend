import { sleep } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';
import { portfolioList, portfolioDetail, portfolioLike } from '../../workloads/portfolio.js';

export const options = {
    scenarios: {
        // 탐색 시나리오 (검색/정렬)
        explorer: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },
                { duration: '2m', target: 50 }, // 검색 부하 집중
                { duration: '30s', target: 0 },
            ],
            exec: 'runExplorer',
        },
        // 좋아요 테러 (동시성)
        liker: {
            executor: 'constant-vus',
            vus: 10,
            duration: '3m',
            exec: 'runLike',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1500'], // 1.5초 이내
    },
};

export function setup() {
    return loginAndGetHeader('http://linkfolio.127.0.0.1.nip.io', 'user_1', 'testPassword123!');
}

export function runExplorer(headers) {
    portfolioList(headers); // 목록 조회
    sleep(1);
    portfolioDetail(headers); // 상세 조회
    sleep(2);
}

export function runLike(headers) {
    portfolioLike(headers); // 좋아요 토글
    sleep(0.5); // 빠르게 반복
}

export function handleSummary(data) {
    return generateSummary(data, "after/isolated/portfolio_stress");
}