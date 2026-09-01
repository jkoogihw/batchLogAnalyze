package com.batch.analyzer;

import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.RuleResult;
import com.batch.policy.PolicyManager;
import com.batch.report.ReportGenerator;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 로그 샘플 폴더(src/test/resources/log_samples) 기반 테스트 검증 모듈
 * 
 * 테스트 리소스에 업로드된 17개의 실제 배치 로그 샘플 파일을 대상으로
 * policy_meta.json의 각 JOB 정책(01~17) 매핑 및 룰 평가(SEARCH, DISPLAY, STEP_METRICS, HOLIDAY)를
 * 정확하게 검증합니다.
 */
public class LogSampleVerificationTest {

    private static File sampleFolder;
    private static File[] logFiles;
    private static List<JobPolicy> policies;

    @BeforeClass
    public static void setUp() throws Exception {
        // 1. log_samples 리소스 폴더 로드
        URL sampleUrl = LogSampleVerificationTest.class.getClassLoader().getResource("log_samples");
        if (sampleUrl != null) {
            sampleFolder = new File(sampleUrl.toURI());
        } else {
            sampleFolder = new File("src/test/resources/log_samples");
        }
        assertTrue("로그 샘플 폴더가 존재해야 합니다: " + sampleFolder.getAbsolutePath(), 
                sampleFolder.exists() && sampleFolder.isDirectory());

        logFiles = sampleFolder.listFiles((dir, name) -> name.endsWith(".log"));
        assertNotNull("샘플 로그 파일 목록이 null이 아니어야 합니다", logFiles);
        assertTrue("최소 17개의 샘플 로그 파일이 존재해야 합니다", logFiles.length >= 17);

        // 2. policy_meta.json 로드 및 파싱
        String json = loadResourceContent("policy_meta.json");
        if (json == null || json.isEmpty()) {
            File rootMeta = new File("policy_meta.json");
            if (rootMeta.exists()) {
                json = Files.readString(rootMeta.toPath(), StandardCharsets.UTF_8);
            }
        }
        assertNotNull("policy_meta.json 정책 데이터를 로드할 수 있어야 합니다", json);

        policies = PolicyManager.parseJsonPolicies(json);
        assertEquals("총 17개의 JOB 정책이 로드되어야 합니다", 17, policies.size());
    }

    /**
     * 클래스패스 리소스 내용 읽기 유틸리티
     */
    private static String loadResourceContent(String resourceName) {
        try (InputStream is = LogSampleVerificationTest.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 특정 JOB No에 해당하는 정책 조회 헬퍼
     */
    private JobPolicy findPolicyByJobNo(String jobNo) {
        return policies.stream()
                .filter(p -> p.jobNo.equals(jobNo))
                .findFirst()
                .orElse(null);
    }

    /**
     * 1. 샘플 로그 폴더 무결성 검증
     * - 폴더 내의 모든 로그 파일이 정상적으로 읽기 가능한지 확인
     */
    @Test
    public void testSampleDirectoryIntegrity() {
        assertNotNull("샘플 로그 파일 목록이 존재해야 함", logFiles);
        assertTrue("로그 샘플 파일이 17개 이상 존재해야 함", logFiles.length >= 17);

        for (File file : logFiles) {
            assertTrue("파일이 읽기 가능해야 함: " + file.getName(), file.canRead());
            assertTrue("파일 크기가 0보다 커야 함: " + file.getName(), file.length() > 0);
        }
    }

    /**
     * 2. 전체 17개 JOB 정책에 대해 샘플 로그 일괄 검증 및 콘솔 요약 출력
     */
    @Test
    public void testAll17SampleLogsBatchVerification() {
        List<CheckResult> results = new ArrayList<>();
        int passCount = 0;
        int failCount = 0;

        for (JobPolicy policy : policies) {
            CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
            results.add(cr);

            // 모든 정책에 대해 매칭되는 샘플 파일이 반드시 발견되어야 함
            assertTrue("JOB [" + policy.jobNo + " : " + policy.jobName + "]의 매칭 로그 파일이 발견되어야 함", 
                    cr.fileFound);
            assertNotNull("파일명이 설정되어야 함", cr.fileName);

            // 규칙 검증 결과가 1개 이상 존재하거나 비영업일 판정이어야 함
            assertTrue("검증 결과 항목이 존재해야 함", cr.ruleResults.size() > 0 || cr.isHoliday);

            if (cr.overallPassed) {
                passCount++;
            } else {
                failCount++;
            }
        }

        assertEquals("17개 JOB 모두 분석 결과가 수집되어야 함", 17, results.size());

        // 콘솔 보고서 출력 테스트
        ReportGenerator.printConsoleReport("log_samples_test", results, policies.size(), passCount, failCount);

        // 마크다운 결과 파일 저장 (기존 결과 파일 삭제 후 재생성)
        File reportDir = new File("report");
        File savedReport = ReportGenerator.saveMarkdownReport(reportDir, "log_samples_test", results, policies.size(), passCount, failCount);
        assertNotNull("리포트 파일이 정상 저장되어야 함", savedReport);
        assertTrue("리포트 파일이 존재해야 함", savedReport.exists());
        assertTrue("리포트 파일 크기가 0보다 커야 함", savedReport.length() > 0);

        System.out.println(">> [검증 완료] 전체: " + policies.size() + ", 통과(PASS): " + passCount + ", 확인필요(FAIL): " + failCount);
    }

    /**
     * 3. 실행할 때마다 기존 결과(리포트) 파일이 삭제되고 새로 생성되는지 검증
     */
    @Test
    public void testReportFileDeletedAndRecreatedOnEachExecution() throws Exception {
        File testDir = new File("build/reports/tests/test_output");
        if (!testDir.exists()) testDir.mkdirs();

        File reportFile = new File(testDir, "로그분석결과_sample_recreate_test.md");

        // 1. 이전 실행 결과 파일(더미 파일) 생성
        String staleContent = "OLD STALE REPORT CONTENT - TO BE DELETED AND OVERWRITTEN";
        Files.writeString(reportFile.toPath(), staleContent, StandardCharsets.UTF_8);
        assertTrue("기존 결과 파일이 존재해야 함", reportFile.exists());
        assertEquals("기존 더미 내용 확인", staleContent, Files.readString(reportFile.toPath(), StandardCharsets.UTF_8));

        // 2. 테스트 분석 결과 데이터 생성
        List<CheckResult> results = new ArrayList<>();
        JobPolicy policy = findPolicyByJobNo("01");
        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
        results.add(cr);

        // 3. 리포트 파일 생성 호출 (내부적으로 기존 파일 삭제 후 재작성 수행)
        File generatedFile = ReportGenerator.saveMarkdownReport(reportFile, "sample_recreate_test", results, 1, cr.overallPassed ? 1 : 0, cr.overallPassed ? 0 : 1);

        // 4. 검증: 기존 더미 내용이 완전히 지워지고 새로운 분석 리포트 내용으로 다시 작성되었는지 확인
        assertNotNull("생성된 파일 객체가 반환되어야 함", generatedFile);
        assertTrue("새로운 결과 파일이 존재해야 함", generatedFile.exists());
        String newContent = Files.readString(generatedFile.toPath(), StandardCharsets.UTF_8);
        assertFalse("이전 더미 내용이 남아있지 않아야 함", newContent.contains("OLD STALE REPORT CONTENT"));
        assertTrue("새로운 리포트 제목이 포함되어야 함", newContent.contains("# 배치로그 분석 결과 보고서 (sample_recreate_test)"));
        assertTrue("JOB별 세부 분석 테이블 헤더가 포함되어야 함", newContent.contains("| 번호 | JOB ID | JOB 이름 | 점검항목 | 점검내용 | 점검결과 |"));
    }

    /**
     * 4. 개별 검증: JOB 01 (gagastJob002 - _10702_)
     * - DISPLAY 규칙: DB Insert GA Count 0건 체크
     */
    @Test
    public void testJob01_GagastJob002() {
        JobPolicy policy = findPolicyByJobNo("01");
        assertNotNull("JOB 01 정책이 존재해야 함", policy);

        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
        assertTrue("파일 매칭 성공", cr.fileFound);
        assertTrue("파일명에 _10702_ 포함 확인", cr.fileName.contains("_10702_"));
        assertEquals("규칙 수 확인", 1, cr.ruleResults.size());

        RuleResult rr = cr.ruleResults.get(0);
        assertEquals("DISPLAY", rr.type);
        assertNotNull("추출값이 존재해야 함", rr.extractedValue);
        assertTrue("0건이거나 0이 추출되어야 함", rr.extractedValue.contains("0"));
        assertTrue("DB Insert GA Count 0건 검증 통과", rr.passed);
        assertTrue("JOB 종합 통과", cr.overallPassed);
    }

    /**
     * 5. 개별 검증: JOB 02 (GagastJob001 - _10701_)
     * - DISPLAY 규칙: DB Insert GA Count 건수확인, FP누락 활동 대상 0건 체크
     */
    @Test
    public void testJob02_GagastJob001() {
        JobPolicy policy = findPolicyByJobNo("02");
        assertNotNull("JOB 02 정책이 존재해야 함", policy);

        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
        assertTrue("파일 매칭 성공", cr.fileFound);
        assertTrue("파일명에 _10701_ 포함 확인", cr.fileName.contains("_10701_"));
        assertEquals("규칙 2개 확인", 2, cr.ruleResults.size());
    }

    /**
     * 6. 개별 검증: JOB 03 (smrmJob101 - _11399_)
     * - DISPLAY 규칙: 불완전판매조사 대상 신규 추출 건 [TB_SMRM1010]
     */
    @Test
    public void testJob03_SmrmJob101() {
        JobPolicy policy = findPolicyByJobNo("03");
        assertNotNull("JOB 03 정책이 존재해야 함", policy);

        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
        assertTrue("파일 매칭 성공", cr.fileFound);
        assertTrue("파일명에 _11399_ 포함 확인", cr.fileName.contains("_11399_"));
        assertEquals("규칙 1개 확인", 1, cr.ruleResults.size());
    }

    /**
     * 7. 개별 검증: JOB 04 & 05 (smrmJob102, smrmJob103 - _11401_, _11402_)
     * - SEARCH 규칙: UMS 발송결과 -> 리턴코드 200 검색
     */
    @Test
    public void testJob04_and_05_SmrmJob102_103() {
        JobPolicy p4 = findPolicyByJobNo("04");
        JobPolicy p5 = findPolicyByJobNo("05");

        CheckResult cr4 = LogAnalyzer.checkJob(sampleFolder, logFiles, p4);
        CheckResult cr5 = LogAnalyzer.checkJob(sampleFolder, logFiles, p5);

        assertTrue("JOB 04 파일 매칭", cr4.fileFound);
        assertTrue("JOB 05 파일 매칭", cr5.fileFound);
        assertTrue("JOB 04 파일명 확인", cr4.fileName.contains("_11401_"));
        assertTrue("JOB 05 파일명 확인", cr5.fileName.contains("_11402_"));
    }

    /**
     * 8. 개별 검증: JOB 06 (smrmJob104 - _11403_)
     * - STEP_METRICS 규칙: StepName : smrmJob104001, ROLLBACK_ZERO
     */
    @Test
    public void testJob06_SmrmJob104_StepMetrics() {
        JobPolicy policy = findPolicyByJobNo("06");
        assertNotNull("JOB 06 정책이 존재해야 함", policy);

        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
        assertTrue("파일 매칭 성공", cr.fileFound);
        assertTrue("파일명에 _11403_ 포함 확인", cr.fileName.contains("_11403_"));
        assertEquals("STEP_METRICS 규칙 확인", 1, cr.ruleResults.size());

        RuleResult rr = cr.ruleResults.get(0);
        assertEquals("STEP_METRICS", rr.type);
        assertNotNull("Step 메트릭 추출 문자열 확인", rr.extractedValue);
        assertTrue("Rollback 0건 판정 확인", rr.passed);
    }

    /**
     * 9. 개별 검증: JOB 07 ~ 10 (smpmJob203, smpmJob207, smpmJob208, smpmJob211)
     */
    @Test
    public void testJob07_to_10_SmpmJobs() {
        String[] jobNos = {"07", "08", "09", "10"};
        String[] expectedPatterns = {"_11259_", "_11262_", "_11263_", "_11266_"};

        for (int i = 0; i < jobNos.length; i++) {
            JobPolicy policy = findPolicyByJobNo(jobNos[i]);
            assertNotNull("JOB " + jobNos[i] + " 정책 존재", policy);

            CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
            assertTrue("JOB " + jobNos[i] + " 파일 매칭 성공", cr.fileFound);
            assertTrue("JOB " + jobNos[i] + " 패턴 포함 확인 (" + expectedPatterns[i] + ")", 
                    cr.fileName.contains(expectedPatterns[i]));
            assertTrue("규칙 결과 존재", cr.ruleResults.size() > 0);
        }
    }

    /**
     * 10. 개별 검증: JOB 11 & 12 (와일드카드 및 접미사 패턴 분기 매칭)
     * - JOB 11: _11268_%16_1 -> 348093_11268_1743036100516_1.log
     * - JOB 12: _11268_%18_1 -> 348093_11268_1743036100518_1.log
     */
    @Test
    public void testJob11_and_12_WildcardSuffixMatching() {
        JobPolicy p11 = findPolicyByJobNo("11");
        JobPolicy p12 = findPolicyByJobNo("12");

        CheckResult cr11 = LogAnalyzer.checkJob(sampleFolder, logFiles, p11);
        CheckResult cr12 = LogAnalyzer.checkJob(sampleFolder, logFiles, p12);

        assertTrue("JOB 11 파일 매칭", cr11.fileFound);
        assertTrue("JOB 12 파일 매칭", cr12.fileFound);

        assertTrue("JOB 11 파일명에 16_1 포함", cr11.fileName.contains("16_1"));
        assertTrue("JOB 12 파일명에 18_1 포함", cr12.fileName.contains("18_1"));
        assertNotEquals("두 JOB은 서로 다른 로그 파일을 매칭해야 함", cr11.fileName, cr12.fileName);
    }

    /**
     * 11. 개별 검증: JOB 13 ~ 17 (smpmJob213, smpmJob220, smpcJob002, smpcJob003, SmpcJob001)
     */
    @Test
    public void testJob13_to_17_Smpm_Smpc_Jobs() {
        String[] jobNos = {"13", "14", "15", "16", "17"};
        String[] expectedPatterns = {"_12833_", "_12871_", "_12928_", "_13104_", "_12926_"};

        for (int i = 0; i < jobNos.length; i++) {
            JobPolicy policy = findPolicyByJobNo(jobNos[i]);
            assertNotNull("JOB " + jobNos[i] + " 정책 존재", policy);

            CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
            assertTrue("JOB " + jobNos[i] + " 파일 매칭 성공", cr.fileFound);
            assertTrue("JOB " + jobNos[i] + " 패턴 포함 확인 (" + expectedPatterns[i] + ")", 
                    cr.fileName.contains(expectedPatterns[i]));
            assertTrue("규칙 결과 존재", cr.ruleResults.size() > 0);
        }
    }

    /**
     * 12. 예외 케이스: 매칭되는 파일이 없는 경우의 동작 검증
     */
    @Test
    public void testTargetFileNotFoundHandling() {
        JobPolicy dummyPolicy = new JobPolicy();
        dummyPolicy.jobNo = "99";
        dummyPolicy.jobName = "nonExistentJob";
        dummyPolicy.jobTitle = "Non Existent Job";
        dummyPolicy.filePrefix = "99_non_existent_";
        dummyPolicy.rawPattern = "_99999_";

        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, dummyPolicy);
        assertFalse("매칭되는 파일이 없으므로 false여야 함", cr.fileFound);
        assertFalse("종합 결과도 실패(false)여야 함", cr.overallPassed);
        assertTrue("파일명에 미발견 안내가 포함되어야 함", cr.fileName.contains("미발견"));
        assertEquals("규칙 결과 1개(파일 미존재 안내)", 1, cr.ruleResults.size());
        assertFalse("규칙 결과 실패", cr.ruleResults.get(0).passed);
    }

    /**
     * 13. CheckLog.runAnalysis 를 통한 logFileSrc 파라미터 연동 엔드투엔드 검증
     */
    @Test
    public void testCheckLogMainWithLogFileSrc() {
        String samplePath = sampleFolder.getAbsolutePath();
        com.batch.CheckLog.AnalysisSummary summary = com.batch.CheckLog.runAnalysis(samplePath);

        assertTrue("CheckLog 분석이 정상 완료되어야 함", summary.success);
        assertEquals("작업 폴더명 일치", sampleFolder.getName(), summary.folderName);
        assertTrue("검증 결과가 존재해야 함", summary.results.size() > 0);
        assertEquals("결과 수가 정책 수와 일치해야 함", summary.totalJobs, summary.results.size());
        assertNotNull("리포트 파일 생성 확인", summary.reportFile);
        assertTrue("리포트 파일 존재 확인", summary.reportFile.exists());
    }
}
