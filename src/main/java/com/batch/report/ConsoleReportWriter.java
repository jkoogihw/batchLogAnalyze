package com.batch.report;

import com.batch.model.CheckResult;
import com.batch.model.RuleResult;

import java.io.File;
import java.util.List;

/**
 * =====================================================================================
 * [구체 리포트 작성기 (Concrete Writer): 콘솔 출력 렌더러]
 * -------------------------------------------------------------------------------------
 * 💡 단일 책임 원칙 (SRP):
 * - 분석 결과를 터미널/콘솔에 가독성 높은 표 형식으로 서식화하여 출력하는 단일 책임을 갖습니다.
 * =====================================================================================
 */
public class ConsoleReportWriter implements ReportWriter {

    @Override
    public File write(String folderName, List<CheckResult> results, int total, int pass, int fail) {
        System.out.println("\n" + "=".repeat(105));
        System.out.println(String.format(" [%s] 배치로그 분석 종합 결과 요약", folderName));
        System.out.println("=".repeat(105));
        System.out.println(String.format(" >> 전체 대상: %d 건 | 정상(PASS): %d 건 | 오류/확인필요(FAIL): %d 건", 
                total, pass, fail));
        System.out.println("-".repeat(105));

        System.out.println(String.format("%-4s | %-48s | %-8s | %s", 
                "No", "JOB 명칭 / 항목", "상태", "추출값 / 상세내용"));
        System.out.println("-".repeat(105));

        if (results != null) {
            for (CheckResult cr : results) {
                String statusBadge = cr.overallPassed ? "[ PASS ]" : "[ FAIL ]";
                String titleWithSchedule = (cr.scheduleInfo != null && !cr.scheduleInfo.trim().isEmpty())
                        ? cr.jobTitle + " (" + cr.scheduleInfo + ")"
                        : cr.jobTitle;
                System.out.println(String.format("%-4s | %-48s | %-8s | 파일: %s", 
                        cr.jobNo, truncate(titleWithSchedule, 48), statusBadge, cr.fileName));

                if (cr.isHoliday) {
                    System.out.println(String.format("     |   %-46s | %-8s | %s", 
                            "-> [비영업일 예외]", "[ PASS ]", cr.holidayDetail));
                } else {
                    for (RuleResult rr : cr.ruleResults) {
                        String rStatus = rr.passed ? "  OK  " : " FAIL ";
                        String ruleNoPrefix = (rr.ruleNo != null && !rr.ruleNo.isEmpty()) ? "[" + rr.ruleNo + "] " : "";
                        System.out.println(String.format("     |   %-46s | %-8s | 추출: %-18s (%s)", 
                                truncate("-> " + ruleNoPrefix + rr.description, 46), rStatus, 
                                rr.extractedValue != null ? rr.extractedValue : "-", rr.message));
                    }
                }
                System.out.println("-".repeat(105));
            }
        }

        return null;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }
}
