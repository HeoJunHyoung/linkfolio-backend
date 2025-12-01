import { sleep } from 'k6';
import { loginAndGetHeader } from '../utils/auth.js';
import { generateSummary } from '../utils/reporter.js';
import { communityList, communityDetail, communityComment } from '../workloads/community.js';

export const options = {
    scenarios: {
        // [시나리오 1] 단순 조회 유저 (다수): 목록 -> 상세 조회 반복
        // -> DB 인덱스 및 캐시 효율성 검증용
        viewer_scenario: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 }, // Warm-up
                { duration: '2m', target: 50 },  // High Load
                { duration: '30s', target: 0 },  // Cool-down
            ],
            exec: 'runViewer',
        },

        // [시나리오 2] 헤비 유저 (소수): 댓글 작성 (Write 부하)
        // -> DB Write Lock 및 트랜잭션 성능 검증용
        writer_scenario: {
            executor: 'constant-vus',
            vus: 5, // 소수의 유저가 지속적으로 댓글 작성
            duration: '3m',
            exec: 'runWriter',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'], // 에러율 1% 미만
        http_req_duration: ['p(95)<1500'], // 95% 요청이 1.5초 이내 (목표 상향)
    },
};

export function setup() {
    // 테스트용 계정 (user_1) 로그인
    return loginAndGetHeader('http://linkfolio.127.0.0.1.nip.io', 'user_1', 'testPassword123!');
}

// 읽기 전용 작업
export function runViewer(headers) {
    communityList(headers);
    sleep(1);
    communityDetail(headers); // Hot Key 조회
    sleep(2);
}

// 쓰기 작업
export function runWriter(headers) {
    communityComment(headers); // 댓글 작성
    sleep(3); // 생각하는 시간
}

export function handleSummary(data) {
    return generateSummary(data, "exploration/community_focus_test");
}