package com.batch.report;

import com.batch.model.CheckResult;

import java.io.File;
import java.util.List;

/**
 * =====================================================================================
 * [구체 리포트 작성기 (Concrete Writer): 콘솔 출력 렌더러]
 * -------------------------------------------------------------------------------------
 * 💡 단일 책임 원칙 (SRP):
 * - 분석 결과를 터미널/콘솔에 # Jobpass 텍스트 보고서 형식으로 출력합니다.
 * =====================================================================================
 */
public class ConsoleReportWriter implements ReportWriter {

    @Override
    public File write(String folderName, List<CheckResult> results, int total, int pass, int fail) {
        String reportText = renderJobpassReport(folderName, results, total, pass, fail);
        System.out.println(reportText);
        return null;
    }

    /**
     * # Jobpass 서식에 맞춘 텍스트 보고서를 생성합니다.
     */
    public static String renderJobpassReport(String folderName, List<CheckResult> results, int total, int pass, int fail) {
        return JobpassReportRenderer.render(folderName, results, total, pass, fail);
    }
}
