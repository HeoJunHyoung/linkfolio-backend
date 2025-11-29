import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export function portfolioList(headers) {
    // 인덱스 테스트용 정렬 조건
    const res = http.get(`${BASE_URL}/portfolio-service/portfolios?page=0&size=10&sort=viewCount,desc`, headers);
    check(res, { 'Portfolio List 200': (r) => r.status === 200 });
}

export function portfolioDetail(headers) {
    const randomId = Math.floor(Math.random() * 100) + 10001;
    const res = http.get(`${BASE_URL}/portfolio-service/portfolios/${randomId}`, headers);
    check(res, { 'Portfolio Detail 200': (r) => r.status === 200 });
}

export function portfolioLike(headers) {
    const portfolioId = Math.floor(Math.random() * 100) + 10001;
    // 좋아요 토글 (Insert/Delete 반복)
    let res = http.post(`${BASE_URL}/portfolio-service/portfolios/${portfolioId}/like`, null, headers);

    if (res.status === 201 || res.status === 409) {
        // 이미 눌렀다면 취소
        http.del(`${BASE_URL}/portfolio-service/portfolios/${portfolioId}/like`, null, headers);
    }
}