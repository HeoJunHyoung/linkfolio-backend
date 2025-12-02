import { sleep } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';

// 워크로드 모듈 로드
import { authLogin } from '../../workloads/auth.js';
import { userGetMe } from '../../workloads/user.js';
import { communityList, communityDetail, communityComment } from '../../workloads/community.js';
import { portfolioList, portfolioDetail, portfolioLike } from '../../workloads/portfolio.js';
import { noticeList } from '../../workloads/support.js';

export const options = {
    scenarios: {
        // [시나리오 A] 커뮤니티 집중 유저 그룹
        community_users: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '2m', target: 30 },
                { duration: '30s', target: 0 },
            ],
            exec: 'runCommunityFlow',
        },

        // [시나리오 B] 포트폴리오 탐색 유저 그룹
        portfolio_users: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '2m', target: 30 },
                { duration: '30s', target: 0 },
            ],
            exec: 'runPortfolioFlow',
        },

        // [시나리오 C] 기본/배경 트래픽 (로그인, 공지 확인 등)
        background_users: {
            executor: 'constant-vus',
            vus: 5,
            duration: '3m',
            exec: 'runBackgroundFlow',
        },
    },
    thresholds: {
        // 전체 에러율 관리
        http_req_failed: ['rate<0.01'],
        // 전체 응답 시간 관리 (통합 환경이므로 기준을 조금 넉넉하게 잡음)
        http_req_duration: ['p(95)<2000'],
    },
};

export function setup() {
    // 통합 테스트용 토큰 발급
    return loginAndGetHeader('http://linkfolio.127.0.0.1.nip.io', 'user_1', 'testPassword123!');
}

// === 실행 함수 ===

export function runCommunityFlow(headers) {
    // [Tag] 태그를 추가하면 Grafana 등에서 필터링 가능 (k6 옵션에 따라 다름)
    communityList(headers);
    communityDetail(headers);
    // 10% 확률로 댓글 작성
    if (Math.random() < 0.1) {
        communityComment(headers);
    }
    sleep(1);
}

export function runPortfolioFlow(headers) {
    portfolioList(headers);
    portfolioDetail(headers);
    // 20% 확률로 좋아요
    if (Math.random() < 0.2) {
        portfolioLike(headers);
    }
    sleep(1);
}

export function runBackgroundFlow(headers) {
    authLogin(); // 로그인 부하 (토큰 발급 X, API 호출만)
    userGetMe(headers);
    noticeList(headers);
    sleep(2);
}

export function handleSummary(data) {
    return generateSummary(data, "after/integrated/global_flow_test");
}