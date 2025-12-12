import { sleep } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';
import { userGetMe, updateUserProfile } from '../../workloads/user.js';

export const options = {
    scenarios: {
        // [시나리오 1] 단순 조회 (가벼운 트래픽, 높은 처리량 기대)
        profile_viewer: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '1m', target: 30 },
                { duration: '30s', target: 0 },
            ],
            exec: 'runViewer',
        },
        // [시나리오 2] 정보 수정 (DB Write Lock 부하 유발)
        profile_updater: {
            executor: 'constant-vus',
            vus: 10, // 소수의 사용자가 지속적으로 수정 시도
            duration: '2m',
            exec: 'runUpdater',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'], // 단순 CRUD이므로 1초 이내 목표
    },
};

export function setup() {
    return loginAndGetHeader('http://linkfolio.127.0.0.1.nip.io', 'user_1', 'testPassword123!');
}

export function runViewer(headers) {
    userGetMe(headers);
    sleep(1);
}

export function runUpdater(headers) {
    updateUserProfile(headers);
    sleep(2); // 빈번한 수정 방지
}

export function handleSummary(data) {
    return generateSummary(data, "after/isolated/user_stress");
}