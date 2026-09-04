package com.batch.analyzer.evaluator;

import com.batch.extract.ValueExtractor;
import com.batch.model.ConditionType;
import com.batch.model.Rule;
import com.batch.model.RuleResult;
import com.batch.model.RuleType;

/**
 * =====================================================================================
 * [구체 전략 (Concrete Strategy): SEARCH 룰 평가기]
 * -------------------------------------------------------------------------------------
 * 💡 단일 책임 원칙 (SRP):
 * - 전체 텍스트 내에서 특정 키워드나 정규식의 '출현 횟수(카운트)'를 계산하고 조건을 판정하는
 *   단일 책임을 가집니다.
 * =====================================================================================
 */
public class SearchRuleEvaluator implements RuleEvaluator {

    public static final RuleType SUPPORTED_TYPE = RuleType.SEARCH;

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
        String description = rule.description != null ? rule.description : rule.target;
        int count = ValueExtractor.countMatches(fullText, rule);
        String extractedValue = count + "건";

        boolean passed;
        String message;

        ConditionType condition = rule.getConditionType();
        switch (condition) {
            case EQUALS_N:
                passed = (count == rule.expectedCount);
                message = passed ? 
                        "정상 (" + count + "건 일치)" : 
                        "불일치 (기대: " + rule.expectedCount + "건, 실제: " + count + "건)";
                break;
            case EQUALS_0:
                passed = (count == 0);
                message = passed ? "정상 (0건)" : "오류 (" + count + "건 발생)";
                break;
            case COUNT_CHECK:
            default:
                passed = true;
                message = "건수확인: " + count + "건";
                break;
        }

        return RuleResult.builder()
                .description(description)
                .type(rule.type)
                .target(rule.target)
                .condition(rule.condition)
                .extractedValue(extractedValue)
                .passed(passed)
                .message(message)
                .build();
    }
}
