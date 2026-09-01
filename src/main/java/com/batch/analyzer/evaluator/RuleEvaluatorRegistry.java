package com.batch.analyzer.evaluator;

import com.batch.model.Rule;
import com.batch.model.RuleResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * =====================================================================================
 * [레지스트리 및 팩토리 패턴 (Registry & Factory Pattern): 룰 평가기 관리자]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 설계 원칙:
 * 1. OCP (Open-Closed Principle):
 *    - 신규 평가기가 등록(register)되면 기존 평가기나 분석기 코드를 건드리지 않고
 *      자동으로 탐색되어 실행됩니다.
 * 2. Fallback 안전장치:
 *    - 등록되지 않은 미지원 룰 타입이 들어오더라도 시스템이 중단되지 않고 알림용 RuleResult를 생성합니다.
 * =====================================================================================
 */
public class RuleEvaluatorRegistry {

    private final List<RuleEvaluator> evaluators = new ArrayList<>();

    public RuleEvaluatorRegistry() {
        // 기본 3대 룰 평가기 등록
        register(new SearchRuleEvaluator());
        register(new DisplayRuleEvaluator());
        register(new StepMetricsRuleEvaluator());
    }

    /**
     * 신규 룰 평가기 등록
     */
    public void register(RuleEvaluator evaluator) {
        if (evaluator != null && !evaluators.contains(evaluator)) {
            evaluators.add(evaluator);
        }
    }

    /**
     * 룰 타입에 일치하는 평가기 탐색
     */
    public RuleEvaluator getEvaluator(String ruleType) {
        if (ruleType == null) return null;
        for (RuleEvaluator evaluator : evaluators) {
            if (evaluator.supports(ruleType)) {
                return evaluator;
            }
        }
        return null;
    }

    /**
     * 룰 평가 실행 (해당 평가기 라우팅 및 폴백 처리)
     */
    public RuleResult evaluate(String fullText, String[] lines, Rule rule) {
        if (rule == null) {
            RuleResult empty = new RuleResult();
            empty.passed = false;
            empty.message = "규칙(Rule) 정보가 null입니다.";
            return empty;
        }

        RuleEvaluator evaluator = getEvaluator(rule.type);
        if (evaluator != null) {
            return evaluator.evaluate(fullText, lines, rule);
        }

        // 미지원 룰 타입 Fallback
        RuleResult fallback = new RuleResult();
        fallback.description = rule.description != null ? rule.description : rule.target;
        fallback.type = rule.type;
        fallback.target = rule.target;
        fallback.condition = rule.condition;
        fallback.extractedValue = "미지원 룰 타입";
        fallback.passed = false;
        fallback.message = "지원하지 않는 룰 타입입니다: " + rule.type;
        return fallback;
    }

    public List<RuleEvaluator> getEvaluators() {
        return Collections.unmodifiableList(evaluators);
    }
}
