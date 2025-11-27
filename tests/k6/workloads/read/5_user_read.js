import http from 'k6/http';
import { check } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';


export const options = {
    vus: 10,
    duration: '30s'
};

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

// 테스트 시작 전 1회 실행 (로그인)
export function setup() {
    // 1번 유저로 로그인해서 토큰 확보
    return loginAndGetHeader(BASE_URL, 'user_1', 'testPassword123!');
}

export default function (headers) {
    const res = http.get(`${BASE_URL}/user-service/users/me`, headers);

    check(res, {
        'Status is 200': (r) => r.status === 200,
        'Has Email': (r) => r.json('email') !== undefined
    });
}

export function handleSummary(data) {
    return generateSummary(data, "read/5_user_read");
}