// 게시글 목록 조회
import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 50, duration: '30s' };
const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export default function () {
    // QueryDSL 검색 조건 테스트
    const res = http.get(`${BASE_URL}/community-service/posts?page=0&size=10&sort=createdAt,desc`);
    check(res, { 'Status is 200': (r) => r.status === 200 });
}