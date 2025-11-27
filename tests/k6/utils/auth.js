// 로그인해서 토큰을 받아오는 역할
import http from 'k6/http';
import { check, fail } from 'k6';

// 로그인 후 헤더(Authorization) 반환
export function loginAndGetHeader(baseUrl, username, password) {

    const payload = JSON.stringify({
        username: username,
        password: password,
    });

    const params = { headers: { 'Content-Type': 'application/json' } };
    const res = http.post(`${baseUrl}/auth-service/auth/login`, payload, params);

    if (res.status !== 200) {
        console.error(`Login failed for ${username}: ${res.status} ${res.body}`);
        fail('Login failed');
    }

    const accessToken = res.json('accessToken');
    return {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${accessToken}`,
        },
    };
}