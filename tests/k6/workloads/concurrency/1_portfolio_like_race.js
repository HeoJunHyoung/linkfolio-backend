import http from 'k6/http';
import { check, sleep } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';

export const options = {
    vus: 10,
    duration: '30s',
};

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export function setup() {
    return loginAndGetHeader(BASE_URL, 'user_1', 'testPassword123!');
}

export default function (headers) {
    // 1번 포트폴리오 집중 공략 (Lock Test)
    const portfolioId = 5000;

    let res = http.post(`${BASE_URL}/portfolio-service/portfolios/${portfolioId}/like`, null, headers);

    check(res, {
        'Like success (201) or Conflict (409)': (r) => r.status === 201 || r.status === 409
    });

    if (res.status === 201 || res.status === 409) {
        res = http.del(`${BASE_URL}/portfolio-service/portfolios/${portfolioId}/like`, null, headers);
        check(res, { 'Unlike success (204)': (r) => r.status === 204 });
    }

    sleep(0.1);
}

// 결과 리포트가 저장될 경로를 'concurrency' 폴더로 지정
export function handleSummary(data) {
    return generateSummary(data, "concurrency/1_portfolio_like_race");
}