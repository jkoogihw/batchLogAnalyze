package com.batch.analyzer.evaluator;

import com.batch.extract.ValueExtractor;
import com.batch.model.ConditionType;
import com.batch.model.Rule;
import com.batch.model.RuleResult;
import com.batch.model.RuleType;

/**
 * =====================================================================================
 * [구체 전략 (Concrete Strategy): DISPLAY 룰 평가기]
 * -------------------------------------------------------------------------------------
 * 💡 단일 책임 원칙 (SRP):
 * - 키워드 뒤에 위치한 수치/문자열 값을 추출하고 조건을 판정하는 단일 책임을 가집니다.
 * =====================================================================================
 */
public class DisplayRuleEvaluator implements RuleEvaluator {

    public static final RuleType SUPPORTED_TYPE = RuleType.DISPLAY;

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
        String extracted = ValueExtractor.extractDisplayValue(fullText, lines, rule);
        String extractedValue = (extracted != null) ? extracted : "미발견";

        Long numValue = ValueExtractor.parseNumber(extracted);
        ConditionType condition = rule.getConditionType();

        boolean passed;
        String message;

        switch (condition) {
            case EQUALS_0:
                if (numValue != null) {
                    passed = (numValue == 0);
                    message = passed ? "정상 (0건)" : "오류 (" + extracted + ")";
                } else if (extracted == null) {
                    passed = false;
                    message = "값 미추출 (항목 미존재)";
                } else {
                    passed = false;
                    message = "수치 파싱 실패 (" + extracted + ")";
                }
                break;

            case EQUALS_N:
                if (numValue != null) {
                    passed = (numValue == rule.expectedCount);
                    message = passed ?
                            "정상 (" + extracted + " 일치)" :
                            "불일치 (기대: " + rule.expectedCount + ", 실제: " + extracted + ")";
                } else {
                    passed = false;
                    message = "수치 파싱 실패 또는 미추출 (" + extracted + ")";
                }
                break;

            case ERROR_IF_PRESENT:
                if (extracted != null && !extracted.trim().isEmpty()) {
                    if (numValue != null && numValue == 0) {
                        passed = true;
                        message = "정상 (0건)";
                    } else {
                        passed = false;
                        message = "오류 발생 (" + extracted + ")";
                    }
                } else {
                    passed = true;
                    message = "정상 (미발생)";
                }
                break;

            case COUNT_CHECK:
            default:
                if (extracted != null) {
                    passed = true;
                    message = "건수확인: " + extracted;
                } else {
                    passed = false;
                    message = "값 미추출 (로그 확인 필요)";
                }
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
