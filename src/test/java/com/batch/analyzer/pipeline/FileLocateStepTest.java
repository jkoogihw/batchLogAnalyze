package com.batch.analyzer.pipeline;

import com.batch.analyzer.JobAnalysisContext;
import com.batch.analyzer.LogFileLocator;
import com.batch.model.CheckResult;
import com.batch.model.CheckStatus;
import com.batch.model.JobPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("단위 테스트: FileLocateStep 로그 파일 탐색 및 LogContent 생성 격리 검증")
class FileLocateStepTest {

    private final FileLocateStep step = new FileLocateStep(new LogFileLocator());

    @Test
    @DisplayName("대상 로그 파일 미발견 시: 파일미발견 마킹 및 파이프라인 조기 종료(TERMINATE) 반환")
    void testExecute_FileNotFound_Terminates() throws Exception {
        JobPolicy policy = JobPolicy.builder("01", "JOB01").filePrefix("01_test_").build();
        JobAnalysisContext jobContext = JobAnalysisContext.builder()
                .logFiles(new File[0])
                .policy(policy)
                .build();

        StepExecutionContext execContext = new StepExecutionContext(jobContext);
        CheckResult result = new CheckResult(policy);

        // [When]
        StepResult stepResult = step.execute(execContext, result);

        // [Then]
        assertTrue(stepResult.isTerminated());
        assertFalse(result.isFileFound());
        assertEquals(CheckStatus.FILE_NOT_FOUND, result.getStatus());
        assertNull(execContext.getLogContent());
    }

    @Test
    @DisplayName("대상 로그 파일 발견 시: CheckResult에 파일 바인딩, LogContent 생성 및 계속(CONTINUE) 진행")
    void testExecute_FileFound_LoadsLogContent(@TempDir Path tempDir) throws IOException, Exception {
        Path logPath = tempDir.resolve("01_test_260822.log");
        Files.writeString(logPath, "2026-08-22 03:00:00 [INFO] 가동\n라인2", StandardCharsets.UTF_8);

        JobPolicy policy = JobPolicy.builder("01", "JOB01").filePrefix("01_test_").build();
        JobAnalysisContext jobContext = JobAnalysisContext.builder()
                .logFiles(new File[]{logPath.toFile()})
                .policy(policy)
                .build();

        StepExecutionContext execContext = new StepExecutionContext(jobContext);
        CheckResult result = new CheckResult(policy);

        // [When]
        StepResult stepResult = step.execute(execContext, result);

        // [Then]
        assertTrue(stepResult.shouldContinue());
        assertTrue(result.isFileFound());
        assertEquals("01_test_260822.log", result.fileName);
        assertNotNull(execContext.getLogContent());
        assertEquals(2, execContext.getLogContent().getLineCount());
    }

    @Test
    @DisplayName("월간 배치 대상 로그 파일 미발견 시: 정상(PASS) 마킹 및 파이프라인 조기 종료(TERMINATE) 반환")
    void testExecute_MonthlyFileNotFound_Passes() throws Exception {
        JobPolicy policy = JobPolicy.monthly("18", "smpmJob206", 2, "00:45");
        policy.filePrefix = "18_smpmJob206_";
        JobAnalysisContext jobContext = JobAnalysisContext.builder()
                .logFiles(new File[0])
                .policy(policy)
                .build();

        StepExecutionContext execContext = new StepExecutionContext(jobContext);
        CheckResult result = new CheckResult(policy);

        // [When]
        StepResult stepResult = step.execute(execContext, result);

        // [Then]
        assertTrue(stepResult.isTerminated());
        assertFalse(result.isFileFound());
        assertTrue(result.isMonthlyNotRun());
        assertTrue(result.isPassed());
        assertEquals(CheckStatus.PASS, result.getStatus());
        assertEquals(1, result.ruleResults.size());
        assertEquals("배치파일점검", result.ruleResults.get(0).description);
        assertTrue(result.ruleResults.get(0).passed);
        assertTrue(result.ruleResults.get(0).message.contains("월간배치 미실행일 (정상)"));
        assertNull(execContext.getLogContent());
    }
}
