package com.batch.analyzer.pipeline;

import com.batch.analyzer.JobAnalysisContext;
import com.batch.analyzer.evaluator.RuleEvaluatorRegistry;
import com.batch.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("단위 테스트: RuleEvaluationStep 개별 규칙 평가 격리 검증")
class RuleEvaluationStepTest {

    private final RuleEvaluationStep step = new RuleEvaluationStep(new RuleEvaluatorRegistry());

    @Test
    @DisplayName("정책에 등록된 복수 룰(SEARCH, DISPLAY)이 순차 평가되어 CheckResult에 누적")
    void testExecute_MultipleRulesEvaluated() throws Exception {
        // [Given]
        LogContent log = LogContent.of("Job Status: SUCCESS\nRead Count: 500\nSkip Count: 0\n");
        JobPolicy policy = JobPolicy.builder("01", "JOB01")
                .addRule(Rule.search("SUCCESS", ConditionType.COUNT_CHECK, "성공 여부 확인"))
                .addRule(Rule.display("Skip Count", ConditionType.EQUALS_0, "스킵 0건 확인"))
                .build();

        JobAnalysisContext jobContext = JobAnalysisContext.builder().policy(policy).build();
        StepExecutionContext execContext = new StepExecutionContext(jobContext);
        execContext.setLogContent(log);

        CheckResult result = new CheckResult(policy);
        result.attachLogFile(new File("test.log"));

        // [When]
        StepResult stepResult = step.execute(execContext, result);

        // [Then]
        assertAll("룰 평가 결과 단언",
                () -> assertTrue(stepResult.shouldContinue()),
                () -> assertEquals(2, result.ruleResults.size()),
                () -> assertTrue(result.isPassed()),
                () -> assertEquals(CheckStatus.PASS, result.getStatus())
        );
    }
}
