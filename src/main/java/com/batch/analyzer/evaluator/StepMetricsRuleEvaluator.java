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
        String targetStep = (rule.stepName != null && !rule.stepName.isEmpty()) ? rule.stepName : rule.target;
        String description = rule.description != null ? rule.description : ("Step 메트릭 (" + targetStep + ")");

        StepMetrics metrics = ValueExtractor.parseStepMetrics(lines, targetStep);

        if (metrics == null) {
            return RuleResult.builder()
                    .description(description)
                    .type(rule.type)
                    .target(targetStep)
                    .condition(rule.condition)
                    .extractedValue("미발견")
                    .passed(false)
                    .message("해당 Step(" + targetStep + ")의 통계 로그를 찾을 수 없습니다.")
                    .build();
        }

        String extractedValue = metrics.toDisplayString();
        ConditionType condition = rule.getConditionType();

        boolean passed;
        String message;

        if (condition == ConditionType.ROLLBACK_ZERO || "ROLLBACK_ZERO".equalsIgnoreCase(rule.condition)) {
            if (metrics.rollbackCount == 0) {
                passed = true;
                message = "정상 (Rollback 0건)";
            } else {
                passed = false;
                message = "오류 (Rollback 발생: " + metrics.rollbackCount + "건)";
            }
        } else {
            passed = true;
            message = "Step 통계 확인";
        }

        return RuleResult.builder()
                .description(description)
                .type(rule.type)
                .target(targetStep)
                .condition(rule.condition)
                .extractedValue(extractedValue)
                .passed(passed)
                .message(message)
                .build();
    }
}
