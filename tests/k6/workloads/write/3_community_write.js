// 댓글 작성
import http from 'k6/http';
import { check } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';

export const options = { vus: 10, duration: '20s' }; // 쓰기는 부하가 크므로 VUs 낮춤
const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export function setup() {
    return loginAndGetHeader(BASE_URL, 'user_1', 'testPassword123!');
}

export default function (headers) {
    const postId = 1; // 1번 게시글에 댓글 폭격
    const payload = JSON.stringify({ content: "부하 테스트 댓글" });

    const res = http.post(`${BASE_URL}/community-service/posts/${postId}/comments`, payload, headers);
    check(res, { 'Comment Created': (r) => r.status === 200 });
}

export function handleSummary(data) {
    return generateSummary(data, "write/3_community_write");
}