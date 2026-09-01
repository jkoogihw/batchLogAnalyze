package com.batch.analyzer.evaluator;

import com.batch.extract.ValueExtractor;
import com.batch.model.Rule;
import com.batch.model.RuleResult;

/**
 * =====================================================================================
 * [구체 전략 (Concrete Strategy): DISPLAY 룰 평가기]
 * -------------------------------------------------------------------------------------
 * 💡 단일 책임 원칙 (SRP):
 * - 특정 키워드/레이블 뒤의 값(단일행, 멀티라인, 콜론/등호 서식)을 추출하고 비교 검증하는
 *   단일 책임을 가집니다.
 * =====================================================================================
 */
public class DisplayRuleEvaluator implements RuleEvaluator {

    public static final String RULE_TYPE = "DISPLAY";

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

        String foundSnippet = ValueExtractor.extractDisplayValue(fullText, lines, rule);

        if (foundSnippet != null) {
            rr.extractedValue = foundSnippet;
            Long numVal = ValueExtractor.parseNumber(foundSnippet);

            if ("EQUALS_0".equalsIgnoreCase(rule.condition)) {
                if (numVal != null) {
                    rr.passed = (numVal == 0);
                    rr.message = rr.passed ? 
                            "정상 (0건)" : 
                            "오류 (추출값: " + foundSnippet + ")";
                } else {
                    rr.passed = false;
                    rr.message = "숫자 파싱 실패 (" + foundSnippet + ")";
                }
            } else if ("ERROR_IF_PRESENT".equalsIgnoreCase(rule.condition)) {
                if (numVal != null) {
                    rr.passed = (numVal == 0);
                    rr.message = rr.passed ? 
                            "정상 (미발생)" : 
                            "오류 (추출값: " + foundSnippet + " - 확인필요)";
                } else {
                    rr.passed = false;
                    rr.message = "숫자 파싱 실패 (" + foundSnippet + ")";
                }
            } else if ("EQUALS_N".equalsIgnoreCase(rule.condition)) {
                if (numVal != null) {
                    rr.passed = (numVal == rule.expectedCount);
                    rr.message = rr.passed ? 
                            "정상 (" + foundSnippet + " 일치)" : 
                            "불일치 (기대: " + rule.expectedCount + ", 실제: " + foundSnippet + ")";
                } else {
                    rr.passed = false;
                    rr.message = "숫자 파싱 실패 (" + foundSnippet + ")";
                }
            } else if ("COUNT_CHECK".equalsIgnoreCase(rule.condition)) {
                rr.passed = true;
                rr.message = "건수확인: " + foundSnippet;
            } else {
                rr.passed = true;
                rr.message = "확인: " + foundSnippet;
            }
        } else {
            rr.extractedValue = "미발견";
            rr.passed = false;
            rr.message = "로그에서 대상 패턴을 찾을 수 없습니다.";
        }

        return rr;
    }
}
