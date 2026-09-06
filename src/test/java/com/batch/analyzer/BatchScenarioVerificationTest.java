package com.batch.analyzer;

import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.policy.PolicyManager;
import com.batch.report.ReportGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [시나리오 테스트 (Scenario / E2E Verification Test)]
 * -------------------------------------------------------------------------------------
 * 💡 검증 목표:
 * 실무에서 발생하는 3대 핵심 배치 실행 시나리오를 정의하고,
 * 각 시나리오별 로그 파일 셋과 정책 메타데이터를 결합하여 엔드-투-엔드(E2E) 분석 및
 * 최종 마크다운 보고서(report/테스트로그분석결과_*.md)를 영속적으로 생성·검증합니다.
 *
 * 📋 3대 시나리오:
 * 1. [시나리오 1] 일간 평일 배치 (sample):
 *    - 일간 로그 17개 + 월간 배치(JOB 18) 미생성 정상 = 18개 전 항목 100% PASS
 *    - 산출물: report/테스트로그분석결과_sample.md
 *
 * 2. [시나리오 2] 월간 배치 포함 정기 실행 (monthly):
 *    - 일간/월간 로그 18개 전수 매칭 = 18개 전 항목 100% PASS
 *    - 산출물: report/테스트로그분석결과_monthly.md
 *
 * 3. [시나리오 3] 비영업일/공휴일 실행 (holiday):
 *    - 비영업일 미실행(JOB 13, 18) 정상 처리
 *    - 비영업일 안내 메시지 감지(JOB 04, 05) 정상 처리
 *    - 일반 휴일 실행 배치(JOB 01~03, 06~12, 14~17) 정상 처리
 *    - 총 18개 전 항목 100% PASS
 *    - 산출물: report/테스트로그분석결과_holiday.md
 * =====================================================================================
 */
@DisplayName("시나리오 테스트: 3대 배치 실행 시나리오(sample, monthly, holiday) 통합 검증")
public class BatchScenarioVerificationTest {

    private static List<JobPolicy> policies;
    private static File reportDir;

    @BeforeAll
    public static void setUp() throws Exception {
        // [Given] 1. 정책 파일 로드
        String json = loadResourceContent("policy_meta.json");
        if (json == null || json.isEmpty()) {
            File rootMeta = new File("src/main/resources/policy_meta.json");
            if (rootMeta.exists()) {
                json = Files.readString(rootMeta.toPath(), StandardCharsets.UTF_8);
            }
        }
        assertNotNull(json, "policy_meta.json 정책 데이터를 로드할 수 있어야 합니다");

        policies = PolicyManager.parseJsonPolicies(json);
        assertEquals(18, policies.size(), "총 18개의 JOB 정책이 로드되어야 합니다");

        // [Given] 2. 결과 리포트 저장 폴더 준비
        reportDir = new File("report");
        if (!reportDir.exists()) {
            reportDir.mkdirs();
        }
    }

    private static String loadResourceContent(String resourceName) {
        try (InputStream is = BatchScenarioVerificationTest.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private File resolveResourceDir(String dirName) {
        File dir = new File("src/test/resources/" + dirName);
        if (!dir.exists()) {
            dir = new File("src/test/resources", dirName);
        }
        return dir;
    }

    /**
     * ---------------------------------------------------------------------------------
     * [시나리오 1] 일간 평일 배치 검증 (sample)
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("시나리오 1: 일간 평일 배치 검증 (17개 로그 + JOB 18 월간미실행 정상 -> 테스트로그분석결과_sample.md)")
    public void testScenario1_DailySample() {
        // [Given] log_samples 디렉터리 및 파일 목록 준비
        File logDir = resolveResourceDir("log_samples");
        assertTrue(logDir.exists() && logDir.isDirectory(), "log_samples 디렉터리가 존재해야 합니다");

        File[] logFiles = logDir.listFiles((d, name) -> name.endsWith(".log"));
        assertNotNull(logFiles, "log_samples 파일 목록이 존재해야 합니다");
        assertTrue(logFiles.length >= 17, "최소 17개의 일간 로그 파일이 존재해야 합니다");

        // [When] 18개 전체 JOB 정책 대상 배치 분석 실행
        List<CheckResult> results = new ArrayList<>();
        int passCount = 0;
        int failCount = 0;

        for (JobPolicy policy : policies) {
            CheckResult cr = LogAnalyzer.checkJob(logDir, logFiles, policy);
            results.add(cr);

            if (cr.overallPassed) passCount++;
            else failCount++;
        }

        // [Then] 결과 단언
        final int totalPass = passCount;
        final int totalFail = failCount;
        assertAll("시나리오 1 (Daily Sample) 분석 결과 단언",
            () -> assertEquals(18, results.size(), "18개 JOB 모두 분석되어야 함"),
            () -> assertEquals(18, totalPass, "18개 JOB 모두 PASS여야 함 (17개 파일 + 1개 월간미실행)"),
            () -> assertEquals(0, totalFail, "FAIL 항목이 없어야 함")
        );

        // [Report Generation] report/테스트로그분석결과_sample.md 생성
        File targetReportFile = new File(reportDir, "테스트로그분석결과_sample.md");
        File savedReport = ReportGenerator.saveMarkdownReport(targetReportFile, "sample", results, policies.size(), passCount, failCount);

        assertNotNull(savedReport, "보고서 파일이 생성되어야 함");
        assertTrue(savedReport.exists() && savedReport.length() > 0, "보고서 파일이 디스크에 존재하고 비어있지 않아야 함");
        System.out.println(">> [시나리오 1 완료] 보고서: " + savedReport.getAbsolutePath());
    }

    /**
     * ---------------------------------------------------------------------------------
     * [시나리오 2] 월간 배치 포함 정기 실행 검증 (monthly)
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("시나리오 2: 월간 배치 포함 검증 (18개 로그 전수 매칭 -> 테스트로그분석결과_monthly.md)")
    public void testScenario2_MonthlyBatch() {
        // [Given] log_monthly 디렉터리 및 파일 목록 준비
        File logDir = resolveResourceDir("log_monthly");
        assertTrue(logDir.exists() && logDir.isDirectory(), "log_monthly 디렉터리가 존재해야 합니다");

        File[] logFiles = logDir.listFiles((d, name) -> name.endsWith(".log"));
        assertNotNull(logFiles, "log_monthly 파일 목록이 존재해야 합니다");
        assertTrue(logFiles.length >= 18, "총 18개의 월간/일간 로그 파일이 존재해야 합니다");

        // [When] 18개 전체 JOB 정책 대상 배치 분석 실행
        List<CheckResult> results = new ArrayList<>();
        int passCount = 0;
        int failCount = 0;

        for (JobPolicy policy : policies) {
            CheckResult cr = LogAnalyzer.checkJob(logDir, logFiles, policy);
            results.add(cr);

            if (cr.overallPassed) {
                passCount++;
            } else {
                failCount++;
                System.out.println(">> [Scenario 2 FAIL] JOB " + cr.jobNo + " (" + cr.jobName + "): " + cr.fileName);
                for (var rr : cr.ruleResults) {
                    if (!rr.passed) {
                        System.out.println("   - Rule " + rr.ruleNo + " [" + rr.description + "] 추출값: " + rr.extractedValue + " / " + rr.message);
                    }
                }
            }
        }

        // [Report Generation] report/테스트로그분석결과_monthly.md 생성
        File targetReportFile = new File(reportDir, "테스트로그분석결과_monthly.md");
        File savedReport = ReportGenerator.saveMarkdownReport(targetReportFile, "monthly", results, policies.size(), passCount, failCount);

        // [Then] 결과 단언
        final int totalPass = passCount;
        final int totalFail = failCount;
        assertAll("시나리오 2 (Monthly Batch) 분석 결과 단언",
            () -> assertEquals(18, results.size(), "18개 JOB 모두 분석되어야 함"),
            () -> assertEquals(18, totalPass, "18개 JOB 모두 PASS여야 함"),
            () -> assertEquals(0, totalFail, "FAIL 항목이 없어야 함")
        );

        assertNotNull(savedReport, "보고서 파일이 생성되어야 함");
        assertTrue(savedReport.exists() && savedReport.length() > 0, "보고서 파일이 디스크에 존재하고 비어있지 않아야 함");
        System.out.println(">> [시나리오 2 완료] 보고서: " + savedReport.getAbsolutePath());
    }

    /**
     * ---------------------------------------------------------------------------------
     * [시나리오 3] 비영업일/공휴일 실행 검증 (holiday)
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("시나리오 3: 비영업일 배치 검증 (16개 로그 + JOB 13/18 미실행 정상 + JOB 04/05 휴일감지 -> 테스트로그분석결과_holiday.md)")
    public void testScenario3_NonWorkDayHoliday() {
        // [Given] log_holiday 디렉터리 및 파일 목록 준비
        File logDir = resolveResourceDir("log_holiday");
        assertTrue(logDir.exists() && logDir.isDirectory(), "log_holiday 디렉터리가 존재해야 합니다");

        File[] logFiles = logDir.listFiles((d, name) -> name.endsWith(".log"));
        assertNotNull(logFiles, "log_holiday 파일 목록이 존재해야 합니다");
        assertTrue(logFiles.length >= 16, "최소 16개의 비영업일 로그 파일이 존재해야 합니다");

        // [When] 18개 전체 JOB 정책 대상 배치 분석 실행
        List<CheckResult> results = new ArrayList<>();
        int passCount = 0;
        int failCount = 0;

        for (JobPolicy policy : policies) {
            CheckResult cr = LogAnalyzer.checkJob(logDir, logFiles, policy);
            results.add(cr);

            if (cr.overallPassed) {
                passCount++;
            } else {
                failCount++;
                System.out.println(">> [Scenario 3 FAIL] JOB " + cr.jobNo + " (" + cr.jobName + "): " + cr.fileName);
                for (var rr : cr.ruleResults) {
                    if (!rr.passed) {
                        System.out.println("   - Rule " + rr.ruleNo + " [" + rr.description + "] 추출값: " + rr.extractedValue + " / " + rr.message);
                    }
                }
            }
        }

        // [Report Generation] report/테스트로그분석결과_holiday.md 생성
        File targetReportFile = new File(reportDir, "테스트로그분석결과_holiday.md");
        File savedReport = ReportGenerator.saveMarkdownReport(targetReportFile, "holiday", results, policies.size(), passCount, failCount);

        // [Then] 결과 단언
        final int totalPass = passCount;
        final int totalFail = failCount;

        // 개별 특수 JOB 검증
        CheckResult job04 = results.stream().filter(r -> "04".equals(r.jobNo)).findFirst().orElse(null);
        CheckResult job05 = results.stream().filter(r -> "05".equals(r.jobNo)).findFirst().orElse(null);
        CheckResult job13 = results.stream().filter(r -> "13".equals(r.jobNo)).findFirst().orElse(null);
        CheckResult job18 = results.stream().filter(r -> "18".equals(r.jobNo)).findFirst().orElse(null);

        assertAll("시나리오 3 (NonWorkDay Holiday) 분석 결과 단언",
            () -> assertEquals(18, results.size(), "18개 JOB 모두 분석되어야 함"),
            () -> assertEquals(18, totalPass, "18개 JOB 모두 PASS여야 함"),
            () -> assertEquals(0, totalFail, "FAIL 항목이 없어야 함"),
            () -> assertNotNull(job04, "JOB 04 결과 존재"),
            () -> assertTrue(job04.isHoliday, "JOB 04는 비영업일 감지 정상 처리되어야 함"),
            () -> assertNotNull(job05, "JOB 05 결과 존재"),
            () -> assertTrue(job05.isHoliday, "JOB 05는 비영업일 감지 정상 처리되어야 함"),
            () -> assertNotNull(job13, "JOB 13 결과 존재"),
            () -> assertTrue(job13.isHoliday || job13.overallPassed, "JOB 13은 비영업일 미실행 정상 처리되어야 함"),
            () -> assertNotNull(job18, "JOB 18 결과 존재"),
            () -> assertTrue(job18.isMonthlyNotRun || job18.overallPassed, "JOB 18은 월간 미실행 정상 처리되어야 함")
        );

        assertNotNull(savedReport, "보고서 파일이 생성되어야 함");
        assertTrue(savedReport.exists() && savedReport.length() > 0, "보고서 파일이 디스크에 존재하고 비어있지 않아야 함");
        System.out.println(">> [시나리오 3 완료] 보고서: " + savedReport.getAbsolutePath());
    }
}
