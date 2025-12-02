import { sleep } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';
import { communityList, communityDetail, communityComment } from '../../workloads/community.js';

export const options = {
    scenarios: {
        // 읽기 부하 (목록/상세) - 캐시 및 인덱스 테스트
        viewer: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },
                { duration: '2m', target: 50 },
                { duration: '30s', target: 0 },
            ],
            exec: 'runViewer',
        },
        // 쓰기 부하 (댓글) - 트랜잭션 및 Lock 테스트
        writer: {
            executor: 'constant-vus',
            vus: 10,
            duration: '3m',
            exec: 'runWriter',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1500'], // 1.5초 이내 목표
    },
};

// [Setup] 테스트 시작 전 1회 로그인하여 토큰 확보
export function setup() {
    // 테스트용 계정 (user_1) - 사전에 setup_data.js로 생성되어 있어야 함
    return loginAndGetHeader('http://linkfolio.127.0.0.1.nip.io', 'user_1', 'testPassword123!');
}

export function runViewer(headers) {
    communityList(headers);
    sleep(1);
    communityDetail(headers); // Hot Key 조회
    sleep(1);
}

export function runWriter(headers) {
    communityComment(headers); // 댓글 작성
    sleep(3); // 생각하는 시간
}

export function handleSummary(data) {
    return generateSummary(data, "before/isolated/community_stress");
}