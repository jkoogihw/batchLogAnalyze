package com.batch.report;

import com.batch.model.CheckResult;

import java.io.File;
import java.util.List;

/**
 * =====================================================================================
 * [퍼사드 (Facade): ReportGenerator]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 리팩토링 포인트:
 * 1. 단일 책임 원칙 (SRP) 준수:
 *    - 콘솔 출력은 ConsoleReportWriter에 위임
 *    - 마크다운 파일 저장은 MarkdownReportWriter에 위임
 * 2. 100% 하위 호환성 유지:
 *    - 기존 printConsoleReport, saveMarkdownReport 정적 메서드를 그대로 유지하여
 *      기존 호출 코드 및 테스트 코드의 변경 없이 동작을 보장합니다.
 * =====================================================================================
 */
public class ReportGenerator {

    private static final ReportWriter consoleWriter = new ConsoleReportWriter();

    /**
     * 콘솔 요약 결과 출력 (ConsoleReportWriter에 위임)
     */
    public static void printConsoleReport(String folderName, List<CheckResult> results, 
                                          int total, int pass, int fail) {
        consoleWriter.write(folderName, results, total, pass, fail);
    }

    /**
     * 마크다운 리포트 파일 생성 및 저장 (기본 설정 경로)
     */
    public static File saveMarkdownReport(String folderName, List<CheckResult> results, 
                                          int total, int pass, int fail) {
        ReportWriter markdownWriter = new MarkdownReportWriter();
        return markdownWriter.write(folderName, results, total, pass, fail);
    }

    /**
     * 마크다운 리포트 파일 생성 및 저장 (대상 디렉터리 또는 파일 지정)
     */
    public static File saveMarkdownReport(File targetDirOrFile, String folderName, List<CheckResult> results, 
                                          int total, int pass, int fail) {
        ReportWriter markdownWriter = new MarkdownReportWriter(targetDirOrFile);
        return markdownWriter.write(folderName, results, total, pass, fail);
    }
}
