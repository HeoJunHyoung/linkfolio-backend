import { sleep } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';
import { noticeList } from '../../workloads/support.js';

export const options = {
    scenarios: {
        // 공지사항 조회 (Read Heavy)
        // 캐싱이 적용되어 있다면 VUs가 높아져도 응답 속도가 매우 빨라야 함
        support_viewer: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 40 },
                { duration: '1m', target: 80 }, // 캐시 성능 테스트를 위해 높은 부하
                { duration: '30s', target: 0 },
            ],
            exec: 'runViewer',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        // 캐시 히트 시 매우 빨라야 하므로 목표를 높게 잡음 (0.5초)
        http_req_duration: ['p(95)<500'],
    },
};

export function setup() {
    // Support API 중 일부는 인증 불필요할 수 있으나, 일관성을 위해 토큰 사용
    return loginAndGetHeader('http://linkfolio.127.0.0.1.nip.io', 'user_1', 'testPassword123!');
}

export function runViewer(headers) {
    noticeList(headers);
    sleep(1);
}

export function handleSummary(data) {
    return generateSummary(data, "before/isolated/support_stress");
}