package com.batch.analyzer.pipeline;

import com.batch.analyzer.evaluator.RuleEvaluatorRegistry;
import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.LogContent;
import com.batch.model.Rule;
import com.batch.model.RuleResult;

/**
 * =====================================================================================
 * [파이프라인 4단계: 개별 규칙(Rule) 평가 (RuleEvaluationStep)]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - RuleEvaluatorRegistry 전략 패턴 엔진을 통해 정책에 정의된 모든 규칙을 순회 검증합니다.
 * =====================================================================================
 */
public class RuleEvaluationStep implements AnalysisStep {

    private final RuleEvaluatorRegistry registry;

    public RuleEvaluationStep(RuleEvaluatorRegistry registry) {
        this.registry = registry != null ? registry : new RuleEvaluatorRegistry();
    }

    @Override
    public StepResult execute(StepExecutionContext context, CheckResult result) throws Exception {
        LogContent logContent = context.getLogContent();
        JobPolicy policy = context.getPolicy();

        if (logContent == null || policy == null || policy.rules == null) {
            return StepResult.next();
        }

        String fullText = logContent.getFullText();
        String[] lines = logContent.getLinesArray();

        for (Rule rule : policy.rules) {
            RuleResult rr = registry.evaluate(fullText, lines, rule);
            result.addRuleResult(rr);
        }

        return StepResult.next();
    }
}
