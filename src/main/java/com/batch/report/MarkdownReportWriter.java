package com.batch.report;

import com.batch.config.Config;
import com.batch.model.CheckResult;
import com.batch.model.RuleResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * =====================================================================================
 * [구체 리포트 작성기 (Concrete Writer): 마크다운 파일 렌더러]
 * -------------------------------------------------------------------------------------
 * 💡 단일 책임 원칙 (SRP):
 * - 분석 결과를 GFM(GitHub Flavored Markdown) 표 및 요약 서식으로 렌더링하고
 *   디스크 파일로 안전하게 저장하는 단일 책임을 갖습니다.
 * - 매 실행 시 기존 결과 파일을 멱등성 있게 삭제 후 새로 작성합니다.
 * =====================================================================================
 */
public class MarkdownReportWriter implements ReportWriter {

    private final File targetDestination;

    public MarkdownReportWriter() {
        this(null);
    }

    public MarkdownReportWriter(File targetDestination) {
        this.targetDestination = targetDestination;
    }

    @Override
    public File write(String folderName, List<CheckResult> results, int total, int pass, int fail) {
        File destination = resolveDestination(folderName);

        // 실행할 때마다 기존 결과 파일이 존재하면 완전히 삭제 후 새로 작성
        try {
            Files.deleteIfExists(destination.toPath());
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

        if (results != null) {
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
        }

        sb.append("\n## 2. 특이사항 및 참고\n");
        sb.append("- 비영업일 실행 시 일부 JOB은 비영업일 안내 메시지 감지 시 정상 처리됩니다.\n");
        sb.append("- RollbackCount 통계는 0건일 때 정상으로 판정됩니다.\n");

        try {
            Files.writeString(destination.toPath(), sb.toString(), StandardCharsets.UTF_8);
            System.out.println("\n>> 마크다운 리포트가 저장되었습니다: " + destination.getAbsolutePath());
            return destination;
        } catch (IOException e) {
            System.err.println("[경고] 리포트 파일 저장 실패: " + e.getMessage());
            return null;
        }
    }

    private File resolveDestination(String folderName) {
        if (targetDestination != null) {
            if (targetDestination.isDirectory() || !targetDestination.getName().endsWith(".md")) {
                if (!targetDestination.exists()) targetDestination.mkdirs();
                return new File(targetDestination, "로그분석결과_" + folderName + ".md");
            } else {
                if (targetDestination.getParentFile() != null && !targetDestination.getParentFile().exists()) {
                    targetDestination.getParentFile().mkdirs();
                }
                return targetDestination;
            }
        }

        String baseFolder = Config.get("base.folder", ".");
        String logAnalysisDir = folderName;
        String reportDir = baseFolder + File.separator + logAnalysisDir;

        File dir = new File(reportDir);
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File("report");
            if (!dir.exists()) dir.mkdirs();
        }

        return new File(dir, "로그분석결과_" + folderName + ".md");
    }

    private static String removeJobNamePrefix(String jobName, String jobTitle) {
        if (jobName == null || jobTitle == null) return jobTitle;
        if (jobTitle.startsWith(jobName)) {
            String result = jobTitle.substring(jobName.length()).trim();
            return result.isEmpty() ? jobTitle : result;
        }
        return jobTitle;
    }
}
