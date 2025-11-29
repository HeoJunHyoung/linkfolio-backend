import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export function userGetMe(headers) {
    const res = http.get(`${BASE_URL}/user-service/users/me`, headers);
    check(res, { 'User GetMe 200': (r) => r.status === 200 });
}