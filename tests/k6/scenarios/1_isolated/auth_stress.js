import { sleep } from 'k6';
import { generateSummary } from '../../utils/reporter.js';
import { authLogin } from '../../workloads/auth.js';

export const options = {
    // [단위 테스트 설정]
    // ㄴ Auth 서비스 하나만 극한으로 테스트하기 위한 설정
    scenarios: {
        login_storm: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '1m', target: 10 },
                { duration: '30s', target: 0 },
            ],
            exec: 'runLogin',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'], // 에러율 1% 미만
        http_req_duration: ['p(95)<1500'], // 로그인 응답 1.5초 이내 목표
    },
};

export function runLogin() {
    authLogin(); // 로그인 시도
    sleep(1);
}

export function handleSummary(data) {
    return generateSummary(data, "after/isolated/auth_stress"); // before/after 지정
}