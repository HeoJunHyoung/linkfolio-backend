// 포트폴리오 목록 조회
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 50,
    duration: '30s',
};

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export default function () {
    // 다양한 정렬 조건 랜덤 요청 (인덱스 튜닝 효과 확인용)
    const sorts = ['createdAt,desc', 'viewCount,desc', 'likeCount,desc'];
    const selectedSort = sorts[Math.floor(Math.random() * sorts.length)];

    // 인증 없이 조회 가능
    const res = http.get(`${BASE_URL}/portfolio-service/portfolios?page=0&size=10&sort=${selectedSort}`);

    check(res, { 'Status is 200': (r) => r.status === 200 });
}