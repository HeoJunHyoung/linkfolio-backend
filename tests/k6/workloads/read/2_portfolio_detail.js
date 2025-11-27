// 포트폴리오 상세 조회
import http from 'k6/http';
import { check } from 'k6';
import { generateSummary } from '../../utils/reporter.js';


export const options = {
    vus: 10,
    duration: '30s',
};

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export default function () {
    // 10,001 ~ 20,000번 사이의 더미 포트폴리오 조회
    const randomId = Math.floor(Math.random() * 10000) + 1;

    // 조회수 증가 로직(Lock) 부하 테스트
    const res = http.get(`${BASE_URL}/portfolio-service/portfolios/${randomId}`);

    check(res, { 'Status is 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
    return generateSummary(data, "read/2_portfolio_detail");
}