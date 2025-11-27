// tests/k6/utils/reporter.js
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.1/index.js";

/**
 * 공통 리포트 생성 함수
 * @param {object} data k6 summary data
 * @param {string} reportName 저장할 폴더/파일명 (예: "read/1_portfolio_list")
 */
export function generateSummary(data, reportName) {
    // 저장 경로 설정 (workloads/read/ 폴더 기준 상위로 3칸 올라가서 results로 진입)
    const basePath = `../../../results/${reportName}`;

    return {
        [`${basePath}/after.html`]: htmlReport(data),
        [`${basePath}/after.json`]: JSON.stringify(data),
        stdout: textSummary(data, { indent: " ", enableColors: true }),
    };
}