import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

// 카테고리 목록 (DB 인덱스 idx_post_category_date 테스트용)
const CATEGORIES = ["QNA", "INFO", "RECRUIT"];

// [Data Setup 정보와 일치시킴]
// setup_data.js에서 10001 ~ 20000번까지 총 10,000개 생성함
const MIN_ID = 1;
const TOTAL_COUNT = 10000;

// [핵심] 핫 데이터 시뮬레이션 (80% 트래픽 -> 상위 5% 게시글)
function getSkewedRandomId() {
    const isHotRequest = Math.random() < 0.8; // 80% 확률로 인기글 조회

    if (isHotRequest) {
        // Hot Data: 상위 5% (10001 ~ 10500) -> Redis 캐시 효율 검증
        const hotRange = Math.floor(TOTAL_COUNT * 0.05);
        return Math.floor(Math.random() * hotRange) + MIN_ID;
    } else {
        // Cold Data: 나머지 95% -> DB 조회 성능(Random I/O) 검증
        return Math.floor(Math.random() * TOTAL_COUNT) + MIN_ID;
    }
}

export function communityList(headers) {
    // 1. 랜덤 카테고리 선택 (인덱스 파티션 테스트)
    const category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];

    const res = http.get(`${BASE_URL}/community-service/posts?category=${category}&page=0&size=10&sort=createdAt,desc`, headers);

    check(res, { 'Community List 200': (r) => r.status === 200 });
}

export function communityDetail(headers) {
    // 데이터가 존재하는 범위(10001~) 내에서 Hot/Cold 조회
    const postId = getSkewedRandomId();

    const res = http.get(`${BASE_URL}/community-service/posts/${postId}`, headers);
    check(res, { 'Community Detail 200': (r) => r.status === 200 });
}

export function communityComment(headers) {
    // 댓글 작성도 인기 게시글에 몰리는 경향 반영
    // -> Hot Post에 댓글이 몰릴 때의 DB Lock 대기 시간(Latency) 증가 확인 가능
    const postId = getSkewedRandomId();

    const payload = JSON.stringify({ content: "Stress Test Comment - Load Testing" });
    const res = http.post(`${BASE_URL}/community-service/posts/${postId}/comments`, payload, headers);

    check(res, { 'Community Comment 200': (r) => r.status === 200 });
}