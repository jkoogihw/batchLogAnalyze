package com.batch.util;

import com.batch.analyzer.LogAnalyzer;
import com.batch.model.ConditionType;
import com.batch.model.JobPolicy;
import com.batch.model.Rule;
import com.batch.model.RuleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [단위 테스트 - LogSlimmer 로그 경량화 유틸리티 검증]
 * -------------------------------------------------------------------------------------
 * 💡 학습 포인트:
 * 1. 대용량 텍스트 축약 및 데이터 무결성 보존:
 *    - 수천 줄의 더미 로그 중 불필요한 라인이 축약 마커(`... [TRIMMED N LINES] ...`)로
 *      대체되면서도, 비즈니스 평가 대상 핵심 라인이 온전하게 보존되는지 검증합니다.
 * 2. 슬림화 전후의 분석 결과 일관성(Determinism):
 *    - 슬림화된 로그 텍스트를 `LogAnalyzer.evaluateRule`에 전달했을 때
 *      원본 로그와 동일하게 PASS 및 정확한 추출값을 반환하는지 단언합니다.
 * =====================================================================================
 */
@DisplayName("단위 테스트: LogSlimmer 테스트용 대용량 로그 경량화 유틸리티")
public class LogSlimmerTest {

    @Test
    @DisplayName("키워드 추출 검증: JobPolicy의 SEARCH, DISPLAY, STEP_METRICS 키워드 수집 확인")
    public void testExtractKeywords() {
        // [Given] 테스트 정책 준비 (Fluent Builder & Rule Factory 활용)
        JobPolicy policy = JobPolicy.builder("01", "testJob")
                .title("Test Job")
                .filePrefix("test_")
                .addRule(Rule.search("HTTP/1.1 200", ConditionType.COUNT_CHECK, "HTTP 200"))
                .addRule(Rule.display("prodList.size", ConditionType.COUNT_CHECK, "Size check"))
                .addRule(Rule.stepMetrics("testStep001", ConditionType.ROLLBACK_ZERO, "Step metrics"))
                .build();

        // [When] 키워드 추출
        Set<String> keywords = LogSlimmer.extractKeywords(policy, null);

        // [Then]
        assertAll("키워드 추출 단언",
            () -> assertTrue(keywords.contains("HTTP/1.1 200"), "SEARCH 타겟 포함"),
            () -> assertTrue(keywords.contains("prodList.size"), "DISPLAY 타겟 포함"),
            () -> assertTrue(keywords.contains("testStep001"), "STEP_METRICS stepName 포함"),
            () -> assertTrue(keywords.contains("StaticLogListener.java"), "시스템 공통 키워드 포함")
        );
    }

    @Test
    @DisplayName("로그 라인 슬림화: 대용량 더미 로그 축약 및 핵심 키워드 보존 검증")
    public void testSlimLines_BasicReduction() {
        // [Given] 1,000 라인의 가상 로그 데이터 생성 (중간에 핵심 키워드 삽입)
        List<String> mockLines = new ArrayList<>();
        mockLines.add("2026-09-02 00:45:03.240 INFO  [Version.java:21] HV000001: Hibernate Validator");
        for (int i = 1; i <= 300; i++) {
            mockLines.add("2026-09-02 00:45:04." + i + " DEBUG [SqlLog.java] DUMMY INSERT " + i);
        }
        mockLines.add("2026-09-02 00:45:10.000 INFO  [Tasklet.java] prodList.size : 40 / delParam : done");
        for (int i = 301; i <= 990; i++) {
            mockLines.add("2026-09-02 00:45:11." + i + " DEBUG [SqlLog.java] DUMMY SELECT " + i);
        }
        mockLines.add("2026-09-02 00:46:21.589 INFO  [StaticLogListener.java:21]      StepName : testStep001");
        mockLines.add("2026-09-02 00:46:21.589 INFO  [StaticLogListener.java:25] RollbackCount : 0");
        mockLines.add("2026-09-02 00:46:21.599 INFO  [SimpleJobLauncher.java:152] Job: [SimpleJob: [name=testJob]] completed with the following parameters: [{run.id=1}] and the following status: [COMPLETED]");

        JobPolicy policy = new JobPolicy("01", "testJob", "Test Job", "test_");
        policy.rules.add(new Rule("DISPLAY", "prodList.size", "COUNT_CHECK", "Size check"));

        // [When] 슬림화 실행
        List<String> slimmed = LogSlimmer.slimLines(mockLines, policy, null);

        // [Then]
        assertAll("슬림화 결과 검증",
            () -> assertTrue(slimmed.size() < 200, "1,000줄이 200줄 이하로 대폭 축약되어야 함 (현재: " + slimmed.size() + "줄)"),
            () -> assertTrue(slimmed.stream().anyMatch(l -> l.contains("prodList.size : 40")), "핵심 키워드 라인 보존"),
            () -> assertTrue(slimmed.stream().anyMatch(l -> l.contains("[TRIMMED")), "축약 마커 포함"),
            () -> assertEquals(mockLines.get(0), slimmed.get(0), "첫 행 타임스탬프 헤더 보존")
        );
    }

    @Test
    @DisplayName("슬림화 후 분석 일관성: LogAnalyzer.evaluateRule 평가 결과가 동일하게 PASS하는지 검증")
    public void testSlimLines_PreservesRuleKeywordsAndEvaluation() {
        // [Given] DISPLAY 검증 라인을 포함한 로그 생성
        List<String> mockLines = new ArrayList<>();
        mockLines.add("2026-09-02 03:00:00.000 INFO [App] Started BatchApplication");
        for (int i = 0; i < 500; i++) {
            mockLines.add("DEBUG repetitive sql trace " + i);
        }
        mockLines.add("2026-09-02 03:01:00.000 INFO [Report] DB Insert GA Count : 17920건");
        for (int i = 0; i < 500; i++) {
            mockLines.add("DEBUG more trace " + i);
        }

        JobPolicy policy = new JobPolicy("02", "gagastJob001", "Gagast Job 1", "02_");
        Rule rule = new Rule("02", "DISPLAY", "DB Insert GA Count", "COUNT_CHECK", "건수확인");
        policy.rules.add(rule);

        // [When] 슬림화 실행
        List<String> slimmed = LogSlimmer.slimLines(mockLines, policy, null);
        String slimmedFullText = String.join("\n", slimmed);
        String[] slimmedLineArr = slimmed.toArray(new String[0]);

        // [Then] 룰 평가 실행
        RuleResult result = LogAnalyzer.evaluateRule(slimmedFullText, slimmedLineArr, rule);

        assertAll("슬림화 로그 대상 룰 평가 검증",
            () -> assertTrue(result.passed, "슬림화된 로그에서도 룰 평가가 PASS해야 함"),
            () -> assertEquals("17920건", result.extractedValue, "추출된 건수가 17920건으로 정확해야 함")
        );
    }

    @Test
    @DisplayName("파일 슬림화 I/O: 임시 파일 대상 slimFile 및 디렉터리 slimDirectory 실행 검증")
    public void testSlimFile_FileIoIntegrity(@TempDir Path tempDir) throws IOException {
        // [Given] 임시 로그 파일 생성
        Path logFilePath = tempDir.resolve("test_job_1.log");
        List<String> rawContent = new ArrayList<>();
        rawContent.add("2026-09-02 01:00:00.000 INFO [Boot] HV000001: Hibernate Validator");
        for (int i = 0; i < 400; i++) {
            rawContent.add("TRACE query line " + i);
        }
        rawContent.add("INFO [Job] HTTP/1.1 200 OK");
        rawContent.add("INFO [Job] completed with the following parameters: [{run.id=1}] and the following status: [COMPLETED]");
        Files.write(logFilePath, rawContent);

        JobPolicy policy = new JobPolicy("01", "testJob", "Test", "test_");
        policy.rules.add(new Rule("SEARCH", "HTTP/1.1 200", "COUNT_CHECK", "HTTP 200"));
        List<JobPolicy> policies = List.of(policy);

        // [When] 디렉터리 일괄 슬림화 실행
        int processed = LogSlimmer.slimDirectory(tempDir.toFile(), policies);

        // [Then]
        List<String> processedLines = Files.readAllLines(logFilePath);
        assertAll("파일 슬림화 I/O 검증",
            () -> assertEquals(1, processed, "1개 파일 처리됨"),
            () -> assertTrue(processedLines.size() < rawContent.size(), "파일 라인 수가 줄어들어야 함"),
            () -> assertTrue(processedLines.stream().anyMatch(l -> l.contains("HTTP/1.1 200")), "키워드 보존")
        );
    }
}
