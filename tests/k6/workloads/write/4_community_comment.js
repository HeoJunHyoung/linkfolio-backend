import http from 'k6/http';
import { check } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';

export const options = { vus: 10, duration: '20s' };
const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export function setup() {
    return loginAndGetHeader(BASE_URL, 'user_1', 'testPassword123!');
}

export default function (headers) {
    // 1번 게시글(seeder에서 생성됨)에 댓글 작성
    const postId = 1;
    const payload = JSON.stringify({ content: "Stress Test Comment" });

    const res = http.post(`${BASE_URL}/community-service/posts/${postId}/comments`, payload, headers);
    check(res, { 'Comment Created': (r) => r.status === 200 });
}

export function handleSummary(data) {
    return generateSummary(data, "write/4_community_comment");
}