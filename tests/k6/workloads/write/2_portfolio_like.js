// 포트폴리오 북마크
import http from 'k6/http';
import { check } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';

export const options = {
    vus: 10,
    duration: '30s',
};

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

// 테스트 시작 전 1회 실행되어 토큰을 받아옴
export function setup() {
    return loginAndGetHeader(BASE_URL, 'user_1', 'testPassword123!');
}

export default function (headers) {
    // 특정 포트폴리오(예: 10001번)에 대해 좋아요/취소 반복 (동시성 테스트)
    const portfolioId = 10001;

    // 좋아요
    let res = http.post(`${BASE_URL}/portfolio-service/portfolios/${portfolioId}/like`, null, headers);
    check(res, { 'Like success (201) or Conflict (409)': (r) => r.status === 201 || r.status === 409 });

    // 취소
    res = http.del(`${BASE_URL}/portfolio-service/portfolios/${portfolioId}/like`, null, headers);
    check(res, { 'Unlike success (204)': (r) => r.status === 204 });
}