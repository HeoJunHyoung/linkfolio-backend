import http from 'k6/http';
import { check, sleep } from 'k6';
import { loginAndGetHeader } from '../utils/auth.js';

export function communityReadScenario(baseUrl, testUser) {
    const authHeaders = loginAndGetHeader(baseUrl, testUser);
    const res = http.get(`${baseUrl}/community-service/posts?page=0&size=10`, authHeaders);
    check(res, { 'Community Read OK': (r) => r.status === 200 });
}

export function communityWriteScenario(baseUrl, testUser) {
    const authHeaders = loginAndGetHeader(baseUrl, testUser);
    const payload = JSON.stringify({
        title: 'Load Test Post',
        content: 'Testing...',
        category: 'INFO',
        isRecruitment: false
    });

    const res = http.post(`${baseUrl}/community-service/posts`, payload, authHeaders);
    check(res, { 'Community Write OK': (r) => r.status === 201 || r.status === 200 });
    sleep(1); // 쓰기는 너무 빠르면 안됨
}