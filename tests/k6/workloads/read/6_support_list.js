// 공지사항 조회
import http from 'k6/http';
import { check } from 'k6';
import { generateSummary } from '../../utils/reporter.js';


export const options = { vus: 10, duration: '30s' };
const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export default function () {
    // Redis 캐싱 적용 전후 비교에 최적
    const res = http.get(`${BASE_URL}/support-service/notices`);
    check(res, { 'Status is 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
    return generateSummary(data, "read/6_support_list");
}