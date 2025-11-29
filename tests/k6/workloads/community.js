import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export function communityList(headers) {
    const res = http.get(`${BASE_URL}/community-service/posts?page=0&size=10&sort=createdAt,desc`, headers);
    check(res, { 'Community List 200': (r) => r.status === 200 });
}

export function communityDetail(headers) {
    // 1번 게시글 집중 조회 (Hot Key -> DB Lock 유발 포인트)
    const postId = 2;
    const res = http.get(`${BASE_URL}/community-service/posts/${postId}`, headers);
    check(res, { 'Community Detail 200': (r) => r.status === 200 });
}

export function communityComment(headers) {
    // 1번 게시글에 댓글 작성 (Write 부하)
    const postId = 2;
    const payload = JSON.stringify({ content: "Stress Test Comment" });
    const res = http.post(`${BASE_URL}/community-service/posts/${postId}/comments`, payload, headers);
    check(res, { 'Community Comment 200': (r) => r.status === 200 });
}