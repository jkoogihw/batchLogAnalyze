package com.batch.report;

import com.batch.model.CheckResult;
import com.batch.model.RuleResult;
import com.batch.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 리포트 생성기
 * 
 * 검증 결과를 콘솔 및 마크다운 형식으로 출력/저장합니다.
 */
public class ReportGenerator {
    
    /**
     * 콘솔 요약 결과 출력
     */
    public static void printConsoleReport(String folderName, List<CheckResult> results, 
                                          int total, int pass, int fail) {
        System.out.println("\n" + "=".repeat(105));
        System.out.println(String.format(" [%s] 배치로그 분석 종합 결과 요약", folderName));
        System.out.println("=".repeat(105));
        System.out.println(String.format(" >> 전체 대상: %d 건 | 정상(PASS): %d 건 | 오류/확인필요(FAIL): %d 건", 
                total, pass, fail));
        System.out.println("-".repeat(105));

        System.out.println(String.format("%-4s | %-48s | %-8s | %s", 
                "No", "JOB 명칭 / 항목", "상태", "추출값 / 상세내용"));
        System.out.println("-".repeat(105));

        for (CheckResult cr : results) {
            String statusBadge = cr.overallPassed ? "[ PASS ]" : "[ FAIL ]";
            System.out.println(String.format("%-4s | %-48s | %-8s | 파일: %s", 
                    cr.jobNo, truncate(cr.jobTitle, 48), statusBadge, cr.fileName));

            if (cr.isHoliday) {
                System.out.println(String.format("     |   %-46s | %-8s | %s", 
                        "-> [비영업일 예외]", "[ PASS ]", cr.holidayDetail));
            } else {
                for (RuleResult rr : cr.ruleResults) {
                    String rStatus = rr.passed ? "  OK  " : " FAIL ";
                    System.out.println(String.format("     |   %-46s | %-8s | 추출: %-18s (%s)", 
                            truncate("-> " + rr.description, 46), rStatus, 
                            rr.extractedValue != null ? rr.extractedValue : "-", rr.message));
                }
            }
            System.out.println("-".repeat(105));
        }
    }

    /**
     * 마크다운 리포트 파일 생성 및 저장 (기본 설정 경로)
     */
    public static File saveMarkdownReport(String folderName, List<CheckResult> results, 
                                          int total, int pass, int fail) {
        String baseFolder = Config.get("base.folder", ".");
        String logAnalysisDir = Config.get("log.analysis.dir", "report");
        String reportDir = baseFolder + File.separator + logAnalysisDir;
        
        File dir = new File(reportDir);
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File("report");
            if (!dir.exists()) dir.mkdirs();
        }
        return saveMarkdownReport(dir, folderName, results, total, pass, fail);
    }

    /**
     * 마크다운 리포트 파일 생성 및 저장 (대상 디렉터리 또는 파일 지정)
     * - 기존 결과 파일이 존재할 경우 삭제 후 새로 작성합니다.
     */
    public static File saveMarkdownReport(File targetDirOrFile, String folderName, List<CheckResult> results, 
                                          int total, int pass, int fail) {
        File reportFile;
        if (targetDirOrFile.isDirectory() || !targetDirOrFile.getName().endsWith(".md")) {
            if (!targetDirOrFile.exists()) targetDirOrFile.mkdirs();
            String reportFileName = "로그분석결과_" + folderName + ".md";
            reportFile = new File(targetDirOrFile, reportFileName);
        } else {
            reportFile = targetDirOrFile;
            if (reportFile.getParentFile() != null && !reportFile.getParentFile().exists()) {
                reportFile.getParentFile().mkdirs();
            }
        }

        // 실행할 때마다 기존 결과 파일이 존재하면 완전히 삭제 후 새로 작성
        try {
            Files.deleteIfExists(reportFile.toPath());
        } catch (IOException e) {
            System.err.println("[경고] 기존 리포트 파일 삭제 실패: " + e.getMessage());
        }

        String timeStamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("# 배치로그 분석 결과 보고서 (").append(folderName).append(")\n\n");
        sb.append("- **분석 일시**: ").append(timeStamp).append("\n");
        sb.append("- **대상 폴더**: `배치로그\\").append(folderName).append("`\n");
        sb.append("- **전체 결과**: 총 **").append(total).append("**개 JOB 중 **")
          .append(pass).append("**개 정상 (PASS), **")
          .append(fail).append("**개 오류/확인필요 (FAIL)\n\n");

        sb.append("## 1. JOB별 세부 분석 내역\n\n");
        sb.append("| 번호 | JOB ID | JOB 이름 | 점검항목 | 점검내용 | 점검결과 |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- | :---: |\n");

        for (CheckResult cr : results) {
            String statusText = cr.overallPassed ? "✅ 정상" : "❌ 오류";
            String cleanedJobTitle = removeJobNamePrefix(cr.jobName, cr.jobTitle);
            
            if (cr.isHoliday) {
                sb.append(String.format("| %s | %s | %s | 비영업일 예외 | %s | %s |\n",
                        cr.jobNo, cr.jobName, cleanedJobTitle, cr.holidayDetail, statusText));
            } else {
                boolean first = true;
                for (RuleResult rr : cr.ruleResults) {
                    String ruleStatus = rr.passed ? "✅" : "❌";
                    String checkItem = "**" + rr.description + "**<br/>`" + 
                            (rr.extractedValue != null ? rr.extractedValue : "-") + "`";
                    String checkContent = ruleStatus + " " + rr.message;
                    if (first) {
                        sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                                cr.jobNo, cr.jobName, cleanedJobTitle, checkItem, checkContent, statusText));
                        first = false;
                    } else {
                        sb.append(String.format("| | | | %s | %s | |\n",
                                checkItem, checkContent));
                    }
                }
            }
        }

        sb.append("\n## 2. 특이사항 및 참고\n");
        sb.append("- 비영업일 실행 시 일부 JOB은 비영업일 안내 메시지 감지 시 정상 처리됩니다.\n");
        sb.append("- RollbackCount 통계는 0건일 때 정상으로 판정됩니다.\n");

        try {
            Files.writeString(reportFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
            System.out.println("\n>> 마크다운 리포트가 저장되었습니다: " + reportFile.getAbsolutePath());
            return reportFile;
        } catch (IOException e) {
            System.err.println("[경고] 리포트 파일 저장 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * JOB 제목에서 JOB 명 중복 제거
     */
    private static String removeJobNamePrefix(String jobName, String jobTitle) {
        if (jobName == null || jobTitle == null) return jobTitle;
        if (jobTitle.startsWith(jobName)) {
            String result = jobTitle.substring(jobName.length()).trim();
            return result.isEmpty() ? jobTitle : result;
        }
        return jobTitle;
    }

    /**
     * 문자열 길이 제한 (말줄임)
     */
    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }
}
