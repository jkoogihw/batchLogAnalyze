package com.batch.report;

import com.batch.model.CheckResult;
import com.batch.service.BatchLogAnalysisService.AnalysisSummary;

import java.io.File;
import java.util.List;

/**
 * =====================================================================================
 * [인터페이스 분리 원칙 (ISP): 리포트 작성 인터페이스]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 설계 원칙:
 * 1. ISP (Interface Segregation Principle):
 *    - 리포트 출력/작성에 필요한 표준 계약을 정의하여 콘솔, 마크다운, 향후 HTML/PDF 등의
 *      다양한 리포트 생성기로 쉽게 확장할 수 있도록 합니다.
 * 2. 편의 메서드 (Data Clumps 해소):
 *    - AnalysisSummary 객체를 직접 전달받아 5개 파라미터 나열을 방지합니다.
 * =====================================================================================
 */
public interface ReportWriter {

    /**
     * 리포트 작성 및 출력 (표준 5개 인자)
     *
     * @param folderName 대상 폴더명
     * @param results    분석 결과 목록
     * @param total      전체 건수
     * @param pass       정상 건수
     * @param fail       실패 건수
     * @return 생성된 리포트 파일 객체 (콘솔 출력의 경우 null 가능)
     */
    File write(String folderName, List<CheckResult> results, int total, int pass, int fail);

    /**
     * AnalysisSummary 기반 리포트 작성 편의 메서드
     */
    default File write(AnalysisSummary summary) {
        if (summary == null) return null;
        return write(summary.folderName, summary.results, summary.totalJobs, summary.passCount, summary.failCount);
    }

    /**
     * 폴더명과 결과 목록 기반 리포트 작성 편의 메서드 (건수 자동 계산)
     */
    default File write(String folderName, List<CheckResult> results) {
        if (results == null) return write(folderName, null, 0, 0, 0);
        int total = results.size();
        int pass = (int) results.stream().filter(CheckResult::isPassed).count();
        int fail = total - pass;
        return write(folderName, results, total, pass, fail);
    }
}
