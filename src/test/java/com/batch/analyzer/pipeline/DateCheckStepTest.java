package com.batch.analyzer.pipeline;

import com.batch.analyzer.JobAnalysisContext;
import com.batch.analyzer.LogDateChecker;
import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.LogContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("단위 테스트: DateCheckStep 로그 일자 검증 격리 검증")
class DateCheckStepTest {

    private final DateCheckStep step = new DateCheckStep(new LogDateChecker());

    @Test
    @DisplayName("로그 파일 타임스탬프와 폴더 기준일이 일치할 때 통과(PASS)")
    void testDateCheckPass() throws Exception {
        // [Given] 2026-08-22 일자의 로그
        LogContent log = LogContent.of("2026-08-22 03:00:00 [INFO] 배치 시작");
        JobPolicy policy = JobPolicy.builder("01", "JOB01")
                .daily("03:00")
                .build();

        JobAnalysisContext jobContext = JobAnalysisContext.builder()
                .folderName("260822")
                .policy(policy)
                .build();

        StepExecutionContext execContext = new StepExecutionContext(jobContext);
        execContext.setLogContent(log);

        CheckResult result = new CheckResult(policy);

        // [When]
        StepResult stepResult = step.execute(execContext, result);

        // [Then]
        assertTrue(stepResult.shouldContinue());
        assertEquals(1, result.ruleResults.size());
        assertTrue(result.ruleResults.get(0).passed);
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("로그 파일 타임스탬프와 폴더 기준일이 불일치할 때 실패(FAIL)")
    void testDateCheckFail() throws Exception {
        // [Given] 2026-08-21 일자의 로그인데 분석 폴더명은 260822
        LogContent log = LogContent.of("2026-08-21 03:00:00 [INFO] 배치 시작");
        JobPolicy policy = JobPolicy.builder("01", "JOB01")
                .daily("03:00")
                .build();

        JobAnalysisContext jobContext = JobAnalysisContext.builder()
                .folderName("260822")
                .policy(policy)
                .build();

        StepExecutionContext execContext = new StepExecutionContext(jobContext);
        execContext.setLogContent(log);

        CheckResult result = new CheckResult(policy);

        // [When]
        StepResult stepResult = step.execute(execContext, result);

        // [Then]
        assertTrue(stepResult.shouldContinue()); // 실패해도 다음 룰 평가를 위해 CONTINUE
        assertEquals(1, result.ruleResults.size());
        assertFalse(result.ruleResults.get(0).passed);
        assertTrue(result.isFailed());
    }
}
