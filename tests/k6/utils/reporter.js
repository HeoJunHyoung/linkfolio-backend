// tests/k6/utils/reporter.js
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.1/index.js";

//
/**
 * 공통 리포트 생성 함수
 * @param {object} data k6 summary data
 * @param {string} reportName 저장할 폴더/파일명 (예: "read/1_portfolio_list")
 */
export function generateSummary(data, reportName) {
    // 저장 경로 설정 (workloads/read/ 폴더 기준 상위로 3칸 올라가서 results로 진입)
    const basePath = `tests/k6/results/${reportName}`;

    return {
        // 1. HTML 리포트 (시각화)
        [`${basePath}.html`]: htmlReport(data),

        // 2. JSON 리포트 (데이터 분석용)
        [`${basePath}.json`]: JSON.stringify(data),

        // 3. TXT 리포트 (요약 텍스트 파일)
        [`${basePath}.txt`]: textSummary(data, { indent: " ", enableColors: false }),

        // 4. 콘솔 출력 (터미널 확인용)
        stdout: textSummary(data, { indent: " ", enableColors: true }),
    };
}