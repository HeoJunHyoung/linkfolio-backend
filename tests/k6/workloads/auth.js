import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export function authLogin() {
    const payload = JSON.stringify({
        username: 'user_1', // 테스트용 고정 유저
        password: 'testPassword123!'
    });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const res = http.post(`${BASE_URL}/auth-service/auth/login`, payload, params);
    check(res, { 'Auth Login 200': (r) => r.status === 200 });
}