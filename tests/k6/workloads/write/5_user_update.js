import http from 'k6/http';
import { check } from 'k6';
import { loginAndGetHeader } from '../../utils/auth.js';
import { generateSummary } from '../../utils/reporter.js';

export const options = {
    vus: 10,
    duration: '30s'
};

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export function setup() {
    return loginAndGetHeader(BASE_URL, 'user_1', 'testPassword123!');
}

export default function (headers) {
    const payload = JSON.stringify({
        name: "Updated Name K6",
        birthdate: "1999-12-31",
        gender: "FEMALE"
    });

    const res = http.put(`${BASE_URL}/user-service/users/me`, payload, headers);

    check(res, { 'Update Success': (r) => r.status === 200 });
}

export function handleSummary(data) {
    return generateSummary(data, "write/5_user_update");
}