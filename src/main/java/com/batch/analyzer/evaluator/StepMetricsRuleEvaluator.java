package com.batch.analyzer.evaluator;

import com.batch.extract.ValueExtractor;
import com.batch.model.Rule;
import com.batch.model.RuleResult;
import com.batch.model.StepMetrics;

/**
 * =====================================================================================
 * [구체 전략 (Concrete Strategy): STEP_METRICS 룰 평가기]
 * -------------------------------------------------------------------------------------
 * 💡 단일 책임 원칙 (SRP):
 * - Spring Batch Step의 실행 메트릭(Read, Write, Commit, Rollback Count)을 파싱하고
 *   롤백 발생 여부를 검증하는 단일 책임을 가집니다.
 * =====================================================================================
 */
public class StepMetricsRuleEvaluator implements RuleEvaluator {

    public static final String RULE_TYPE = "STEP_METRICS";

    @Override
    public boolean supports(String ruleType) {
        return RULE_TYPE.equalsIgnoreCase(ruleType);
    }

    @Override
    public RuleResult evaluate(String fullText, String[] lines, Rule rule) {
        RuleResult rr = new RuleResult();
        rr.description = rule.description != null ? rule.description : ("StepName : " + rule.stepName);
        rr.type = rule.type;
        rr.target = rule.stepName;
        rr.condition = rule.condition;

        StepMetrics metrics = ValueExtractor.parseStepMetrics(lines, rule.stepName);

        if (metrics != null) {
            rr.extractedValue = metrics.toDisplayString();

            if ("ROLLBACK_ZERO".equalsIgnoreCase(rule.condition)) {
                rr.passed = (metrics.rollbackCount == 0);
                rr.message = rr.passed ? 
                        "정상 (Rollback 0건)" : 
                        "오류 (RollbackCount: " + metrics.rollbackCount + "건 발생)";
            } else {
                rr.passed = true;
                rr.message = "Step 통계 확인 완료";
            }
        } else {
            rr.extractedValue = "Step 통계 미발견";
            rr.passed = false;
            rr.message = "해당 StepName(" + rule.stepName + ")의 통계 정보를 찾을 수 없습니다.";
        }

        return rr;
    }
}
