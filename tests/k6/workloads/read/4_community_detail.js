import http from 'k6/http';
import { check } from 'k6';
import { generateSummary } from '../../utils/reporter.js';


export const options = {
    vus: 10,
    duration: '30s'
};

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export default function () {
    // 데이터 세팅 시 10,000개의 게시글을 만들었으므로 그 범위 내에서 랜덤 조회
    // (실제 DB에 생성된 ID 범위를 확인해보세요. 보통 1~10000 이거나 auto_increment에 따라 다를 수 있습니다.)
    // 안전하게 1~1000 사이를 조회한다고 가정
    const randomId = Math.floor(Math.random() * 1000) + 1;

    const res = http.get(`${BASE_URL}/community-service/posts/${randomId}`);

    check(res, {
        'Status is 200': (r) => r.status === 200,
        'Content Check': (r) => r.json('id') === randomId // 응답 ID 확인
    });
}

export function handleSummary(data) {
    return generateSummary(data, "read/4_community_detail");
}