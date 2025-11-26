import http from 'k6/http';
import { check, fail } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export function createTestUser(baseUrl) {
    const uniqueId = randomString(8);
    const testUser = {
        email: `test_${uniqueId}@linkfolio.com`,
        username: `user_${uniqueId}`,
        password: 'testPassword123!',
        name: 'K6 User',
        birthdate: '1990-01-01',
        gender: 'MALE'
    };

    const res = http.post(
        `${baseUrl}/auth-service/auth/test/setup-user`,
        JSON.stringify(testUser),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (res.status !== 200) {
        console.error(`User creation failed: ${res.body}`);
        fail('Failed to create test user');
    }

    // 백도어가 반환한 User ID (Long) 추가
    testUser.id = res.json();
    return testUser;
}

export function loginAndGetHeader(baseUrl, user) {
    const loginPayload = JSON.stringify({
        username: user.username,
        password: user.password,
    });

    const res = http.post(
        `${baseUrl}/auth-service/auth/login`,
        loginPayload,
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (res.status !== 200) {
        fail(`Login failed for user ${user.username}`);
    }

    const accessToken = res.json('accessToken');
    return {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${accessToken}`,
        },
    };
}