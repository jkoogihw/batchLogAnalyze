package com.batch.analyzer.evaluator;

import com.batch.extract.ValueExtractor;
import com.batch.model.*;

/**
 * =====================================================================================
 * [구체 전략 (Concrete Strategy): STEP_METRICS 룰 평가기]
 * -------------------------------------------------------------------------------------
 * 💡 단일 책임 원칙 (SRP):
 * - Spring Batch Step의 실행 메트릭(Read/Write/Commit/Rollback)을 분석하고 판정하는
 *   단일 책임을 가집니다.
 * =====================================================================================
 */
public class StepMetricsRuleEvaluator implements RuleEvaluator {

    public static final RuleType SUPPORTED_TYPE = RuleType.STEP_METRICS;

    @Override
    public boolean supports(String ruleType) {
        return SUPPORTED_TYPE.getCode().equalsIgnoreCase(ruleType);
    }

    @Override
    public boolean supports(RuleType ruleType) {
        return SUPPORTED_TYPE == ruleType;
    }

    @Override
    public RuleResult evaluate(String fullText, String[] lines, Rule rule) {
        RuleResult rr = new RuleResult();
        String targetStep = (rule.stepName != null && !rule.stepName.isEmpty()) ? rule.stepName : rule.target;

        rr.description = rule.description != null ? rule.description : ("Step 메트릭 (" + targetStep + ")");
        rr.type = rule.type;
        rr.target = targetStep;
        rr.condition = rule.condition;

        StepMetrics metrics = ValueExtractor.parseStepMetrics(lines, targetStep);

        if (metrics == null) {
            rr.extractedValue = "미발견";
            rr.passed = false;
            rr.message = "해당 Step(" + targetStep + ")의 통계 로그를 찾을 수 없습니다.";
            return rr;
        }

        rr.extractedValue = metrics.toDisplayString();
        ConditionType condition = rule.getConditionType();

        if (condition == ConditionType.ROLLBACK_ZERO || "ROLLBACK_ZERO".equalsIgnoreCase(rule.condition)) {
            if (metrics.rollbackCount == 0) {
                rr.passed = true;
                rr.message = "정상 (Rollback 0건)";
            } else {
                rr.passed = false;
                rr.message = "오류 (Rollback 발생: " + metrics.rollbackCount + "건)";
            }
        } else {
            rr.passed = true;
            rr.message = "Step 통계 확인";
        }

        return rr;
    }
}
