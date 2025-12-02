import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

//
export function noticeList(headers) {
    const res = http.get(`${BASE_URL}/support-service/notices?page=0&size=10`, headers);
    check(res, { 'Notice List 200': (r) => r.status === 200 });
}