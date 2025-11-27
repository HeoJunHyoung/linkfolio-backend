// 로그인
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 10, // 동시 접속 50명
    duration: '30s',
};

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export default function () {
    // 1~100번 유저 중 랜덤 선택
    const randomId = Math.floor(Math.random() * 100) + 1;
    const payload = JSON.stringify({
        username: `user_${randomId}`,
        password: 'testPassword123!'
    });
    const params = { headers: { 'Content-Type': 'application/json' } };

    // 로그인 요청 (Bcrypt 연산 부하 측정)
    const res = http.post(`${BASE_URL}/auth-service/auth/login`, payload, params);

    check(res, {
        'Login Success': (r) => r.status === 200,
        'Token Exists': (r) => r.json('accessToken') !== undefined,
    });
}