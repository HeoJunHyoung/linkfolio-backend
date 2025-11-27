// 공지사항 조회
import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 100, duration: '30s' }; // 캐싱 테스트용으로 VUs 높임
const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export default function () {
    // Redis 캐싱 적용 전후 비교에 최적
    const res = http.get(`${BASE_URL}/support-service/notices`);
    check(res, { 'Status is 200': (r) => r.status === 200 });
}