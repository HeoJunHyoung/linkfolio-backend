import http from 'k6/http';
import { check } from 'k6';

// 고객센터는 인증 없이 조회
export function supportScenario(baseUrl) {
    const res = http.get(`${baseUrl}/support-service/notices`);
    check(res, { 'Notice List OK': (r) => r.status === 200 });
}