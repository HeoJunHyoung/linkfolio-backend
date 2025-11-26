import http from 'k6/http';
import { check } from 'k6';
import { loginAndGetHeader } from '../utils/auth.js';

export function portfolioScenario(baseUrl, testUser) {
    const authHeaders = loginAndGetHeader(baseUrl, testUser);

    // 목록 조회 (페이징)
    const res = http.get(`${baseUrl}/portfolio-service/portfolios?page=0&size=10`, authHeaders);
    check(res, { 'Portfolio List OK': (r) => r.status === 200 });
}