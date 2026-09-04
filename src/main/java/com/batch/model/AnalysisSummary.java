package com.batch.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * =====================================================================================
 * [배치 분석 종합 집계 도메인 모델 (Aggregate Model): AnalysisSummary]
 * -------------------------------------------------------------------------------------
 * 💡 역할 및 책임:
 * 1. 도메인 집계 및 상태 관리 ("Tell, Don't Ask"):
 *    - 분석 대상 폴더, 개별 JOB 검증 결과 목록(`results`), 정상/실패 카운트를 일관되게 보관합니다.
 *    - `addResult(CheckResult)` 호출 시 내부적으로 passCount, failCount를 자동 집계하여
 *      데이터 무결성을 스스로 보장합니다.
 * 2. 최상위 도메인 모델로 승격:
 *    - 서비스 레이어의 내부 클래스에서 `com.batch.model` 최상위 패키지로 이동하여
 *      리포트 생성기, 서비스, CLI 등 모든 계층에서 일관된 도메인 모델로 재사용됩니다.
 * =====================================================================================
 */
public class AnalysisSummary {

    public File workFolder;
    public String folderName;
    public List<CheckResult> results = new ArrayList<>();
    public int totalJobs;
    public int passCount;
    public int failCount;
    public boolean success; // 정상 로그 분석 성공 시 true, 대상 폴더/파일 부재로 실패 리포트 생성 시 false
    public File reportFile;

    public AnalysisSummary() {
    }

    public AnalysisSummary(AnalysisSummary other) {
        if (other != null) {
            this.workFolder = other.workFolder;
            this.folderName = other.folderName;
            this.results = new ArrayList<>(other.results);
            this.totalJobs = other.totalJobs;
            this.passCount = other.passCount;
            this.failCount = other.failCount;
            this.success = other.success;
            this.reportFile = other.reportFile;
        }
    }

    /**
     * 검증 결과를 추가하고 정상/실패 카운트를 자동 집계합니다
     */
    public void addResult(CheckResult cr) {
        if (cr != null) {
            results.add(cr);
            if (cr.isPassed()) {
                passCount++;
            } else {
                failCount++;
            }
        }
    }

    public boolean isAllPassed() {
        return success && failCount == 0 && passCount == totalJobs;
    }

    public int getTotalJobs() {
        return totalJobs;
    }

    public int getPassCount() {
        return passCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public boolean isSuccess() {
        return success;
    }

    public File getReportFile() {
        return reportFile;
    }
}
