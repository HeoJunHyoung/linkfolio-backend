import { sleep } from 'k6';
import { loginAndGetHeader } from '../utils/auth.js';
import { generateSummary } from '../utils/reporter.js';

// 위에서 만든 워크로드 모듈들 가져오기
import { authLogin } from '../workloads/auth.js';
import { userGetMe } from '../workloads/user.js';
import { communityList, communityDetail, communityComment } from '../workloads/community.js';
import { portfolioList, portfolioDetail, portfolioLike } from '../workloads/portfolio.js';
import { noticeList } from '../workloads/support.js';

// [테스트 시나리오 설정]
export const options = {
    scenarios: {
        // 1. [Community] 트래픽 집중 (조회수 Lock, 댓글 쓰기 부하)
        // -> 여기가 메인 병목 지점이 될 것임
        community_scenario: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 3 },  // [Warmup] 서서히 예열
                { duration: '1m', target: 10 },   // [Load] 본격 부하 (상세 조회 집중)
                { duration: '30s', target: 0 },   // [Cooldown]
            ],
            exec: 'runCommunity',
        },

        // 2. [Portfolio] 검색 및 탐색 (DB 인덱스, 네트워크 I/O 부하)
        portfolio_scenario: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 3 },
                { duration: '1m', target: 10 },
                { duration: '30s', target: 0 },
            ],
            exec: 'runPortfolio',
        },

        // 3. [Base] 기타 서비스 (인증, 유저, 공지사항 - 배경 트래픽)
        base_scenario: {
            executor: 'constant-vus',
            vus: 10,
            duration: '2m',
            exec: 'runBase',
        },
    },
};

// [초기화] 테스트 시작 전 1회 로그인하여 토큰 확보
export function setup() {
    return loginAndGetHeader('http://linkfolio.127.0.0.1.nip.io', 'user_1', 'testPassword123!');
}

// === 실제 실행 함수들 ===

// 커뮤니티 흐름: 목록 -> 상세(Hot Post) -> 댓글 작성
export function runCommunity(headers) {
    communityList(headers);
    communityDetail(headers); // 여기서 DB CPU 튈 예정
    if (Math.random() < 0.1) { // 10% 확률로 댓글 작성 (쓰기 부하)
        communityComment(headers);
    }
    sleep(1);
}

// 포트폴리오 흐름: 목록 -> 상세 -> 좋아요
export function runPortfolio(headers) {
    portfolioList(headers);
    portfolioDetail(headers);
    if (Math.random() < 0.2) { // 20% 확률로 좋아요
        portfolioLike(headers);
    }
    sleep(1);
}

// 기본 흐름: 로그인, 내정보, 공지사항 (가볍게)
export function runBase(headers) {
    authLogin(); // 로그인 부하
    userGetMe(headers);
    noticeList(headers);
    sleep(2);
}

export function handleSummary(data) {
    // tests/k6/results/exploration/global_load_test.{html,json,txt} 로 저장됨
    return generateSummary(data, "exploration/global_load_test");
}