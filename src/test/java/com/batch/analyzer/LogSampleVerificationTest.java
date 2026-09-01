package com.batch.analyzer;

import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.RuleResult;
import com.batch.policy.PolicyManager;
import com.batch.report.ReportGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [통합 테스트 (Integration Test) 학습 예제]
 * -------------------------------------------------------------------------------------
 * 💡 학습 포인트:
 * 1. 통합 테스트의 정의:
 *    - 개별 단위(함수/클래스)를 넘어 실제 파일 시스템(I/O), JSON 파서, 정규식 엔진, 리포트 생성기 등
 *      여러 컴포넌트가 유기적으로 상호작용하여 의도한 비즈니스 목적을 달성하는지 검증합니다.
 * 2. 테스트 픽스처(Fixture) 관리 (@BeforeAll):
 *    - 매 테스트마다 무거운 대용량 리소스를 반복 로딩하지 않고, 클래스 초기화 시 1회만 공유 픽스처로
 *      구성하여 테스트 수행 속도(약 20~30초 단축)와 힙 메모리를 최적화합니다.
 * 3. BDD (Behavior-Driven Development) 테스트 작성 패턴:
 *    - [Given] : 테스트를 실행하기 위한 사전 조건 및 입력 데이터 준비
 *    - [When]  : 실제 검증 대상 로직 또는 API 호출 실행
 *    - [Then]  : 기대하는 결과값 및 상태 검증 (단언문 Assertion)
 * 4. JUnit 5의 assertAll (그룹 단언문):
 *    - 단일 단언문 실패 시 이후 검증이 중단되는 기존 방식과 달리, 모든 검증 항목을 끝까지 실행하여
 *      어느 부분이 실패했는지 한 번에 종합적으로 보고받을 수 있습니다.
 * =====================================================================================
 */
@DisplayName("통합 테스트: 17개 실제 배치 로그 샘플 및 비즈니스 정책 검증")
public class LogSampleVerificationTest {

    private static File sampleFolder;
    private static File[] logFiles;
    private static List<JobPolicy> policies;

    /**
     * [공유 픽스처 초기화 - @BeforeAll]
     * JUnit 5에서는 @BeforeAll 어노테이션이 붙은 메서드는 기본적으로 static이어야 합니다.
     * 모든 테스트 케이스가 실행되기 전에 단 한 번 실행됩니다.
     */
    @BeforeAll
    public static void setUp() throws Exception {
        // [Given] 1. 테스트 리소스 폴더(log_samples) 경로 로드
        URL sampleUrl = LogSampleVerificationTest.class.getClassLoader().getResource("log_samples");
        if (sampleUrl != null) {
            sampleFolder = new File(sampleUrl.toURI());
        } else {
            sampleFolder = new File("src/test/resources/log_samples");
        }
        assertTrue(sampleFolder.exists() && sampleFolder.isDirectory(), 
                "로그 샘플 폴더가 존재해야 합니다: " + sampleFolder.getAbsolutePath());

        // [Given] 2. 대상 .log 파일 목록 스캔
        logFiles = sampleFolder.listFiles((dir, name) -> name.endsWith(".log"));
        assertNotNull(logFiles, "샘플 로그 파일 목록이 null이 아니어야 합니다");
        assertTrue(logFiles.length >= 17, "최소 17개의 샘플 로그 파일이 존재해야 합니다");

        // [Given] 3. policy_meta.json 정책 파일 로드 및 객체 파싱
        String json = loadResourceContent("policy_meta.json");
        if (json == null || json.isEmpty()) {
            File rootMeta = new File("policy_meta.json");
            if (rootMeta.exists()) {
                json = Files.readString(rootMeta.toPath(), StandardCharsets.UTF_8);
            }
        }
        assertNotNull(json, "policy_meta.json 정책 데이터를 로드할 수 있어야 합니다");

        policies = PolicyManager.parseJsonPolicies(json);
        assertEquals(17, policies.size(), "총 17개의 JOB 정책이 로드되어야 합니다");
    }

    /**
     * 클래스패스 리소스 내용 읽기 헬퍼 메서드
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
     * 특정 JOB 번호(01~17)로 정책을 조회하는 헬퍼 메서드
     */
    private JobPolicy findPolicyByJobNo(String jobNo) {
        return policies.stream()
                .filter(p -> p.jobNo.equals(jobNo))
                .findFirst()
                .orElse(null);
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 1] 파일 시스템 무결성 검증
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 통합 테스트 수행 전, 테스트 대상이 되는 외부 데이터/파일들이 읽기 가능한 상태인지 선행 검증합니다.
     */
    @Test
    @DisplayName("1. 샘플 로그 디렉터리 무결성 및 파일 읽기 권한 검증")
    public void testSampleDirectoryIntegrity() {
        // [Given] setUp에서 로드된 logFiles 배열
        // [When & Then] 각 파일이 실제로 존재하고 비어있지 않으며 읽기 가능한지 전수 검사
        assertNotNull(logFiles, "샘플 로그 파일 목록이 존재해야 함");
        assertTrue(logFiles.length >= 17, "로그 샘플 파일이 17개 이상 존재해야 함");

        for (File file : logFiles) {
            assertAll("파일 무결성 검증 - " + file.getName(),
                () -> assertTrue(file.canRead(), () -> "파일이 읽기 가능해야 함: " + file.getName()),
                () -> assertTrue(file.length() > 0, () -> "파일 크기가 0보다 커야 함: " + file.getName())
            );
        }
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 2] 17개 JOB 전체 배치 분석 및 결과 리포트 출력 통합 검증
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 시스템 전체 워크플로우(파일 매핑 -> 룰 분석 -> 결과 집계 -> 콘솔/마크다운 리포트 생성)를
     *   한 번의 흐름으로 통합 검증합니다.
     */
    @Test
    @DisplayName("2. 전체 17개 JOB 정책에 대해 샘플 로그 일괄 검증 및 리포트 파일 생성")
    public void testAll17SampleLogsBatchVerification() {
        // [Given] 17개 JOB 정책과 샘플 로그 파일 준비
        List<CheckResult> results = new ArrayList<>();
        int passCount = 0;
        int failCount = 0;

        // [When] 전체 JOB에 대한 로그 분석 실행
        for (JobPolicy policy : policies) {
            CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
            results.add(cr);

            // [Then - 개별 JOB 매핑 검증]
            assertTrue(cr.fileFound, () -> "JOB [" + policy.jobNo + " : " + policy.jobName + "]의 매칭 로그 파일이 발견되어야 함");
            assertNotNull(cr.fileName, "파일명이 설정되어야 함");
            assertTrue(cr.ruleResults.size() > 0 || cr.isHoliday, "검증 결과 항목이 존재해야 함");

            if (cr.overallPassed) passCount++;
            else failCount++;
        }

        // [Then - 전체 집계 결과 검증]
        assertEquals(17, results.size(), "17개 JOB 모두 분석 결과가 수집되어야 함");

        // [When] 콘솔 및 마크다운 리포트 생성
        ReportGenerator.printConsoleReport("log_samples_test", results, policies.size(), passCount, failCount);

        File reportDir = new File("report");
        File savedReport = ReportGenerator.saveMarkdownReport(reportDir, "log_samples_test", results, policies.size(), passCount, failCount);

        // [Then - 생성된 마크다운 파일 검증]
        assertAll("리포트 파일 생성 상태 검증",
            () -> assertNotNull(savedReport, "리포트 파일이 정상 저장되어야 함"),
            () -> assertTrue(savedReport.exists(), "리포트 파일이 디스크에 존재해야 함"),
            () -> assertTrue(savedReport.length() > 0, "리포트 파일 크기가 0보다 커야 함")
        );

        System.out.println(">> [통합 검증 완료] 전체: " + policies.size() + ", 통과(PASS): " + passCount + ", 확인필요(FAIL): " + failCount);
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 3] 리포트 파일의 라이프사이클 (삭제 후 재생성) 검증
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 부수 효과(Side-Effect) 검증: 파일 쓰기 시 기존 파일 내용이 덮어쓰여지지 않고 찌꺼기가 남는 현상을
     *   방지하기 위해, 사전에 더미(Stale) 파일을 심어두고 완전 삭제 후 새로 쓰이는지 검증합니다.
     */
    @Test
    @DisplayName("3. 실행할 때마다 기존 결과 파일이 완전 삭제되고 최신 내용으로 재생성되는지 검증")
    public void testReportFileDeletedAndRecreatedOnEachExecution() throws Exception {
        // [Given] 1. 테스트용 임시 디렉터리 및 과거 더미 리포트 파일 생성
        File testDir = new File("build/reports/tests/test_output");
        if (!testDir.exists()) testDir.mkdirs();

        File reportFile = new File(testDir, "로그분석결과_sample_recreate_test.md");
        String staleContent = "OLD STALE REPORT CONTENT - TO BE DELETED AND OVERWRITTEN";
        Files.writeString(reportFile.toPath(), staleContent, StandardCharsets.UTF_8);

        // 사전 상태 확인
        assertTrue(reportFile.exists(), "기존 더미 파일이 존재해야 함");
        assertEquals(staleContent, Files.readString(reportFile.toPath(), StandardCharsets.UTF_8), "기존 더미 내용 일치 확인");

        // [Given] 2. 분석 결과 데이터 1건 준비
        List<CheckResult> results = new ArrayList<>();
        JobPolicy policy = findPolicyByJobNo("01");
        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);
        results.add(cr);

        // [When] ReportGenerator 호출 (내부적으로 기존 파일 deleteIfExists 후 신규 생성)
        File generatedFile = ReportGenerator.saveMarkdownReport(reportFile, "sample_recreate_test", results, 1, cr.overallPassed ? 1 : 0, cr.overallPassed ? 0 : 1);

        // [Then] 과거 내용이 소멸되고 새로운 리포트 마크다운 구조가 올바르게 기록되었는지 검증
        assertAll("리포트 파일 재생성 검증",
            () -> assertNotNull(generatedFile, "생성된 파일 객체가 반환되어야 함"),
            () -> assertTrue(generatedFile.exists(), "새로운 결과 파일이 디스크에 존재해야 함"),
            () -> {
                String newContent = Files.readString(generatedFile.toPath(), StandardCharsets.UTF_8);
                assertFalse(newContent.contains("OLD STALE REPORT CONTENT"), "이전 더미 내용이 남아있지 않아야 함 (완전 삭제 확인)");
                assertTrue(newContent.contains("# 배치로그 분석 결과 보고서 (sample_recreate_test)"), "신규 리포트 헤더 제목 포함 확인");
                assertTrue(newContent.contains("| 번호 | JOB ID | JOB 이름 | 점검항목 | 점검내용 | 점검결과 |"), "테이블 헤더 포함 확인");
            }
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 4] 개별 JOB 01: DISPLAY 규칙 (0건 체크) 단위/통합 검증
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 비즈니스 요구사항: DB Insert GA Count가 반드시 0건이어야 정상(PASS)으로 판정되는 로직을 검증합니다.
     */
    @Test
    @DisplayName("4. JOB 01 (gagastJob002) - DB Insert GA Count 0건 정상 체크")
    public void testJob01_GagastJob002() {
        // [Given] JOB 01 정책 준비
        JobPolicy policy = findPolicyByJobNo("01");
        assertNotNull(policy, "JOB 01 정책이 존재해야 함");

        // [When] JOB 01 로그 분석 실행
        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);

        // [Then] 파일 매칭 및 룰 검증 결과 단언
        assertAll("JOB 01 분석 결과 검증",
            () -> assertTrue(cr.fileFound, "파일 매칭 성공"),
            () -> assertTrue(cr.fileName.contains("_10702_"), "파일명에 rawPattern _10702_ 포함 확인"),
            () -> assertEquals(1, cr.ruleResults.size(), "검증 규칙 수 1개"),
            () -> {
                RuleResult rr = cr.ruleResults.get(0);
                assertEquals("DISPLAY", rr.type);
                assertNotNull(rr.extractedValue, "추출값이 null이 아니어야 함");
                assertTrue(rr.extractedValue.contains("0"), "0건이거나 0이 추출되어야 함");
                assertTrue(rr.passed, "DB Insert GA Count 0건 검증 통과 (PASS)");
            },
            () -> assertTrue(cr.overallPassed, "JOB 종합 결과 PASS")
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 5] 개별 JOB 02: 다중 규칙 (건수확인 + 0건 체크) 검증
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("5. JOB 02 (GagastJob001) - 다중 룰(건수확인 & FP누락 0건) 동시 검증")
    public void testJob02_GagastJob001() {
        // [Given] JOB 02 정책
        JobPolicy policy = findPolicyByJobNo("02");
        assertNotNull(policy, "JOB 02 정책이 존재해야 함");

        // [When] 분석 실행
        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);

        // [Then] 2개의 세부 규칙이 모두 정상 수집되었는지 확인
        assertAll("JOB 02 다중 룰 검증",
            () -> assertTrue(cr.fileFound, "파일 매칭 성공"),
            () -> assertTrue(cr.fileName.contains("_10701_"), "파일명 패턴 확인"),
            () -> assertEquals(2, cr.ruleResults.size(), "2개의 규칙(GA Count + FP누락) 검증 수집")
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 6] 개별 JOB 03: 신규 추출 건수 표기 검증
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("6. JOB 03 (smrmJob101) - 불완전판매 신규 추출 건수 정상 파싱")
    public void testJob03_SmrmJob101() {
        JobPolicy policy = findPolicyByJobNo("03");
        assertNotNull(policy, "JOB 03 정책이 존재해야 함");

        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);

        assertAll("JOB 03 검증",
            () -> assertTrue(cr.fileFound, "파일 매칭 성공"),
            () -> assertTrue(cr.fileName.contains("_11399_"), "파일명 패턴 확인"),
            () -> assertEquals(1, cr.ruleResults.size(), "규칙 1개 확인")
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 7] 개별 JOB 04 & 05: SEARCH 규칙 (HTTP 리턴코드 200 검색 1건) 검증
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("7. JOB 04 & 05 (smrmJob102, smrmJob103) - UMS 리턴코드 200 검색 검증")
    public void testJob04_and_05_SmrmJob102_103() {
        JobPolicy p4 = findPolicyByJobNo("04");
        JobPolicy p5 = findPolicyByJobNo("05");

        CheckResult cr4 = LogAnalyzer.checkJob(sampleFolder, logFiles, p4);
        CheckResult cr5 = LogAnalyzer.checkJob(sampleFolder, logFiles, p5);

        assertAll("JOB 04 및 05 파일 매칭 검증",
            () -> assertTrue(cr4.fileFound, "JOB 04 파일 매칭"),
            () -> assertTrue(cr5.fileFound, "JOB 05 파일 매칭"),
            () -> assertTrue(cr4.fileName.contains("_11401_"), "JOB 04 파일명 확인"),
            () -> assertTrue(cr5.fileName.contains("_11402_"), "JOB 05 파일명 확인")
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 8] 개별 JOB 06: STEP_METRICS 규칙 (Rollback 0건 판정) 검증
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("8. JOB 06 (smrmJob104) - Spring Batch Step 메트릭 및 Rollback 0건 검증")
    public void testJob06_SmrmJob104_StepMetrics() {
        JobPolicy policy = findPolicyByJobNo("06");
        assertNotNull(policy, "JOB 06 정책이 존재해야 함");

        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);

        assertAll("STEP_METRICS 룰 검증",
            () -> assertTrue(cr.fileFound, "파일 매칭 성공"),
            () -> assertTrue(cr.fileName.contains("_11403_"), "파일명 패턴 확인"),
            () -> assertEquals(1, cr.ruleResults.size(), "STEP_METRICS 규칙 확인"),
            () -> {
                RuleResult rr = cr.ruleResults.get(0);
                assertEquals("STEP_METRICS", rr.type);
                assertNotNull(rr.extractedValue, "Step 메트릭 추출 문자열 확인");
                assertTrue(rr.passed, "Rollback 0건 판정 통과 (PASS)");
            }
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 9] 개별 JOB 07 ~ 10: 반복 그룹 검증
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("9. JOB 07 ~ 10 (smpmJob203, 207, 208, 211) - 일괄 정책 매핑 검증")
    public void testJob07_to_10_SmpmJobs() {
        String[] jobNos = {"07", "08", "09", "10"};
        String[] expectedPatterns = {"_11259_", "_11262_", "_11263_", "_11266_"};

        for (int i = 0; i < jobNos.length; i++) {
            String jobNo = jobNos[i];
            String pattern = expectedPatterns[i];
            JobPolicy policy = findPolicyByJobNo(jobNo);

            assertNotNull(policy, () -> "JOB " + jobNo + " 정책 존재 확인");
            CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);

            assertAll("JOB " + jobNo + " 검증",
                () -> assertTrue(cr.fileFound, () -> "JOB " + jobNo + " 파일 매칭 성공"),
                () -> assertTrue(cr.fileName.contains(pattern), () -> "JOB " + jobNo + " 파일명에 " + pattern + " 포함 확인"),
                () -> assertTrue(cr.ruleResults.size() > 0, "규칙 결과 1개 이상 존재")
            );
        }
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 10] 와일드카드 및 접미사 패턴 분기 매칭 검증 (JOB 11 & 12)
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 동일한 기본 식별자(`_11268_`)를 갖는 두 파일이 있을 때, 와일드카드 `%` 및 고유 접미사(`16_1` vs `18_1`)를
     *   통해 서로 다른 대상 파일로 정확히 분기 매핑되는지 검증합니다.
     */
    @Test
    @DisplayName("10. JOB 11 & 12 - 동일 식별자 내 와일드카드 접미사(%16_1, %18_1) 고유 분기 매칭 검증")
    public void testJob11_and_12_WildcardSuffixMatching() {
        // [Given] JOB 11 (한생)과 JOB 12 (한손) 정책 준비
        JobPolicy p11 = findPolicyByJobNo("11");
        JobPolicy p12 = findPolicyByJobNo("12");

        // [When] 각각 매칭 실행
        CheckResult cr11 = LogAnalyzer.checkJob(sampleFolder, logFiles, p11);
        CheckResult cr12 = LogAnalyzer.checkJob(sampleFolder, logFiles, p12);

        // [Then] 각각 서로 다른 고유 파일로 분기 매칭되었는지 확인
        assertAll("와일드카드 분기 매칭 검증",
            () -> assertTrue(cr11.fileFound, "JOB 11 파일 매칭 성공"),
            () -> assertTrue(cr12.fileFound, "JOB 12 파일 매칭 성공"),
            () -> assertTrue(cr11.fileName.contains("16_1"), "JOB 11은 16_1 파일 매칭"),
            () -> assertTrue(cr12.fileName.contains("18_1"), "JOB 12는 18_1 파일 매칭"),
            () -> assertNotEquals(cr11.fileName, cr12.fileName, "두 JOB은 절대 동일한 파일을 매칭해서는 안 됨")
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 11] 개별 JOB 13 ~ 17: 상품비교 및 파기/징구 JOB 일괄 검증
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("11. JOB 13 ~ 17 (smpmJob213, smpmJob220, smpcJob002, 003, 001) 검증")
    public void testJob13_to_17_Smpm_Smpc_Jobs() {
        String[] jobNos = {"13", "14", "15", "16", "17"};
        String[] expectedPatterns = {"_12833_", "_12871_", "_12928_", "_13104_", "_12926_"};

        for (int i = 0; i < jobNos.length; i++) {
            String jobNo = jobNos[i];
            String pattern = expectedPatterns[i];
            JobPolicy policy = findPolicyByJobNo(jobNo);

            assertNotNull(policy, () -> "JOB " + jobNo + " 정책 존재");
            CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, policy);

            assertAll("JOB " + jobNo + " 검증",
                () -> assertTrue(cr.fileFound, () -> "JOB " + jobNo + " 파일 매칭 성공"),
                () -> assertTrue(cr.fileName.contains(pattern), () -> "JOB " + jobNo + " 패턴 일치 확인"),
                () -> assertTrue(cr.ruleResults.size() > 0, "규칙 결과 존재")
            );
        }
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 12] 예외 케이스: 대상 파일 미존재 시의 실패 처리 검증
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 네거티브 테스트 (Negative Test): 비정상 상황(존재하지 않는 가상의 JOB 정책)에서도
     *   시스템이 NullPointerException 등으로 죽지 않고 우아하게 실패(FAIL) 처리되는지 검증합니다.
     */
    @Test
    @DisplayName("12. 예외 처리: 매칭되는 로그 파일이 없을 때 안전한 미발견(FAIL) 처리 검증")
    public void testTargetFileNotFoundHandling() {
        // [Given] 존재하지 않는 가상의 더미 정책 생성
        JobPolicy dummyPolicy = new JobPolicy();
        dummyPolicy.jobNo = "99";
        dummyPolicy.jobName = "nonExistentJob";
        dummyPolicy.jobTitle = "Non Existent Job";
        dummyPolicy.filePrefix = "99_non_existent_";
        dummyPolicy.rawPattern = "_99999_";

        // [When] 가상 정책으로 분석 시도
        CheckResult cr = LogAnalyzer.checkJob(sampleFolder, logFiles, dummyPolicy);

        // [Then] 안전한 실패 상태 전이 확인
        assertAll("파일 미존재 예외 처리 검증",
            () -> assertFalse(cr.fileFound, "매칭되는 파일이 없으므로 fileFound == false"),
            () -> assertFalse(cr.overallPassed, "종합 결과 overallPassed == false"),
            () -> assertTrue(cr.fileName.contains("미발견"), "파일명에 미발견 표기 포함"),
            () -> assertEquals(1, cr.ruleResults.size(), "파일 미존재 안내 규칙 1건 등록"),
            () -> assertFalse(cr.ruleResults.get(0).passed, "규칙 결과 passed == false")
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [테스트 13] 엔드투엔드(E2E) 파라미터 연동 분석 검증
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - End-to-End 테스트: 사용자가 CLI 또는 상위 애플리케이션에서 `CheckLog.runAnalysis(path)`를
     *   호출했을 때 반환되는 최종 DTO(`AnalysisSummary`)와 리포트 파일 생성을 완결성 있게 확인합니다.
     */
    @Test
    @DisplayName("13. E2E 검증: CheckLog.runAnalysis(path) 전체 실행 및 요약 DTO 검증")
    public void testCheckLogMainWithLogFileSrc() {
        // [Given] 샘플 폴더 절대 경로
        String samplePath = sampleFolder.getAbsolutePath();

        // [When] CheckLog 엔트리포인트 분석 실행
        com.batch.CheckLog.AnalysisSummary summary = com.batch.CheckLog.runAnalysis(samplePath);

        // [Then] 종합 요약 DTO 및 리포트 파일 유효성 검증
        assertAll("CheckLog E2E 실행 결과 검증",
            () -> assertTrue(summary.success, "전체 분석 상태 성공(true)"),
            () -> assertEquals(sampleFolder.getName(), summary.folderName, "작업 폴더명 일치"),
            () -> assertTrue(summary.results.size() > 0, "검증 결과 리스트 비어있지 않음"),
            () -> assertEquals(summary.totalJobs, summary.results.size(), "분석 결과 수가 정책 수와 일치"),
            () -> assertNotNull(summary.reportFile, "리포트 파일 객체 null 아님"),
            () -> assertTrue(summary.reportFile.exists(), "리포트 파일 디스크 생성 확인")
        );
    }
}
