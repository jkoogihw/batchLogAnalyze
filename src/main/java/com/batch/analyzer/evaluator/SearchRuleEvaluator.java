package com.batch.analyzer.evaluator;

import com.batch.extract.ValueExtractor;
import com.batch.model.Rule;
import com.batch.model.RuleResult;

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

    public static final String RULE_TYPE = "SEARCH";

    @Override
    public boolean supports(String ruleType) {
        return RULE_TYPE.equalsIgnoreCase(ruleType);
    }

    @Override
    public RuleResult evaluate(String fullText, String[] lines, Rule rule) {
        RuleResult rr = new RuleResult();
        rr.description = rule.description != null ? rule.description : rule.target;
        rr.type = rule.type;
        rr.target = rule.target;
        rr.condition = rule.condition;

        int count = ValueExtractor.countMatches(fullText, rule);
        rr.extractedValue = count + "건";

        if ("EQUALS_N".equalsIgnoreCase(rule.condition)) {
            rr.passed = (count == rule.expectedCount);
            rr.message = rr.passed ? 
                    "정상 (" + count + "건 일치)" : 
                    "불일치 (기대: " + rule.expectedCount + "건, 실제: " + count + "건)";
        } else if ("COUNT_CHECK".equalsIgnoreCase(rule.condition)) {
            rr.passed = true;
            rr.message = "건수확인: " + count + "건";
        } else if ("EQUALS_0".equalsIgnoreCase(rule.condition)) {
            rr.passed = (count == 0);
            rr.message = rr.passed ? "정상 (0건)" : "오류 (" + count + "건 발생)";
        } else {
            rr.passed = true;
            rr.message = "건수확인: " + count + "건";
        }

        return rr;
    }
}
