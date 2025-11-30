import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    // 동시성 변수를 배제하기 위해 딱 1명으로 테스트
    vus: 1,
    duration: '30s',
};

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export default function () {
    // DB 조회 없이 문자열만 반환하는 가벼운 API 호출
    const res = http.get(`${BASE_URL}/user-service/welcome`);

    check(res, {
        'Status is 200': (r) => r.status === 200,
    });

    sleep(1);
}