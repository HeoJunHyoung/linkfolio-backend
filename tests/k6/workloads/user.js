import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

//
export function userGetMe(headers) {
    const res = http.get(`${BASE_URL}/user-service/users/me`, headers);
    check(res, { 'User GetMe 200': (r) => r.status === 200 });
}
// 내 정보 수정 (DB Write 부하 테스트용)
export function updateUserProfile(headers) {
    const payload = JSON.stringify({
        name: `Updated User ${Math.floor(Math.random() * 1000)}`,
        birthdate: '1995-05-05',
        gender: Math.random() < 0.5 ? 'MALE' : 'FEMALE'
    });

    const res = http.put(`${BASE_URL}/user-service/users/me`, payload, headers);
    check(res, { 'User Update 200': (r) => r.status === 200 });
}