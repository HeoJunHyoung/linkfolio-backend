import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

// 직군 목록
const POSITIONS = [
    "BACKEND", "FRONTEND", "FULLSTACK", "DEVOPS", "AI_ML", "MOBILE", "DESIGN", "PM"
];

// [Data Setup 정보]
// ID 범위: 10001 ~ 20000 (총 10,000개)
const MIN_ID = 1;
const TOTAL_COUNT = 10000;

// [핵심] 핫 데이터 시뮬레이션 함수
// 80%의 확률로 상위 5% 데이터를, 20% 확률로 나머지 데이터를 조회
function getSkewedRandomId() {
    const isHotRequest = Math.random() < 0.8; // 80% 확률로 Hot Data 조회

    if (isHotRequest) {
        // Hot Data: 상위 5% (10001 ~ 10500) - 캐시 히트 유도
        const hotRange = Math.floor(TOTAL_COUNT * 0.05); // 500개
        return Math.floor(Math.random() * hotRange) + MIN_ID;
    } else {
        // Cold Data: 전체 범위 (10001 ~ 20000) - 캐시 미스(DB 접근) 유도 (Long-tail)
        return Math.floor(Math.random() * TOTAL_COUNT) + MIN_ID;
    }
}

export function portfolioList(headers) {
    // 직군은 8개뿐이므로 랜덤 선택해도 캐시 히트율이 높음 (12.5% 확률로 겹침)
    const randomPosition = POSITIONS[Math.floor(Math.random() * POSITIONS.length)];

    // 캐시 키(Key) 다양화를 위해 정렬 조건도 랜덤하게 섞어서 테스트
    // -> 실제 유저는 '최신순'을 가장 많이 보므로 lastModifiedAt에 가중치를 줄 수도 있음
    const sort = Math.random() < 0.7 ? 'lastModifiedAt,desc' : 'popularityScore,desc';

    const queryParams = `page=0&size=10&position=${randomPosition}&sort=${sort}`;
    const res = http.get(`${BASE_URL}/portfolio-service/portfolios?${queryParams}`, headers);

    check(res, { 'Portfolio List 200': (r) => r.status === 200 });
}

export function portfolioDetail(headers) {
    // [수정됨] 80% 확률로 인기 게시글을 조회하여 캐시 효과 검증
    const targetId = getSkewedRandomId();

    const res = http.get(`${BASE_URL}/portfolio-service/portfolios/${targetId}`, headers);
    check(res, { 'Portfolio Detail 200': (r) => r.status === 200 });
}

export function portfolioLike(headers) {
    // 좋아요 역시 인기 게시글에 몰리는 경향이 있으므로 동일한 ID 로직 사용
    const portfolioId = getSkewedRandomId();

    let res = http.post(`${BASE_URL}/portfolio-service/portfolios/${portfolioId}/like`, null, headers);

    // 이미 좋아요 상태라면 취소 (Toggle 시뮬레이션)
    if (res.status === 201 || res.status === 409) {
        http.del(`${BASE_URL}/portfolio-service/portfolios/${portfolioId}/like`, null, headers);
    }
}