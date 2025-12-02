import { seedRealUsers, seedDummyPortfolios, seedCommunityPosts, seedSupportData } from '../../utils/seeder.js';

const BASE_URL = 'http://linkfolio.127.0.0.1.nip.io';

export const options = {
    scenarios: {
        setup: {
            executor: 'shared-iterations', // 정해진 횟수(1회)만큼 실행
            vus: 1,
            iterations: 1,
            maxDuration: '30m',
        },
    },
};

export default function () {
    console.log("🚀 START: Data Seeding...");

    // 1. 로그인 테스트용 '진짜 유저' 100명 (user_1 ~ user_100)
    seedRealUsers(BASE_URL, 1, 100);

    // 2. 조회 부하용 '더미 포트폴리오' 10,000개 (ID 10001 ~ 20000)
    seedDummyPortfolios(BASE_URL, 10001, 10000);

    // 3. 커뮤니티 게시글 10,000개 (작성자는 user_1에게 몰아주기)
    seedCommunityPosts(BASE_URL, 10001, 10000);
    // *작성자를 더미 유저 ID(10001)로 설정하여, 조회 시 작성자 정보 null 처리 테스트도 겸함

    // 4. 공지사항/FAQ 1,000개 (1회 호출당 30/30개 생성 -> 약 34번 반복)
    seedSupportData(BASE_URL, 34);

    console.log("✅ COMPLETE: All data seeded!");
}