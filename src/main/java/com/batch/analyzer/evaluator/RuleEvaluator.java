package com.batch.analyzer.evaluator;

import com.batch.model.Rule;
import com.batch.model.RuleResult;

/**
 * =====================================================================================
 * [전략 패턴 (Strategy Pattern) 인터페이스: 룰 평가기]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 설계 원칙:
 * 1. OCP (Open-Closed Principle: 개방-폐쇄 원칙):
 *    - 새로운 룰 타입(예: SQL_COUNT, REGEX_PATTERN, TIMEOUT_CHECK 등)이 추가되더라도
 *      기존 LogAnalyzer의 코드를 전혀 수정하지 않고, RuleEvaluator 인터페이스를 구현한
 *      새로운 클래스를 추가하는 것만으로 시스템을 확장할 수 있습니다.
 * 2. ISP (Interface Segregation Principle: 인터페이스 분리 원칙):
 *    - 룰 평가에 필요한 최소한의 명세(supports, evaluate)만을 정의하여 결합도를 낮춥니다.
 * 3. LSP (Liskov Substitution Principle: 리스코프 치환 원칙):
 *    - 모든 구현체는 동일한 입출력 규약을 준수하며 예외 없이 RuleResult 객체를 반환합니다.
 * =====================================================================================
 */
public interface RuleEvaluator {

    /**
     * 해당 평가기가 주어진 룰 타입을 처리할 수 있는지 여부 확인
     *
     * @param ruleType 룰 타입 문자열 (예: "SEARCH", "DISPLAY", "STEP_METRICS")
     * @return 지원 여부 (true / false)
     */
    boolean supports(String ruleType);

    /**
     * 로그 텍스트 및 라인 데이터를 기반으로 룰 검증 수행
     *
     * @param fullText 전체 로그 원본 텍스트
     * @param lines    개행 기준 분할된 로그 라인 배열
     * @param rule     평가할 룰 메타데이터 객체
     * @return 룰 검증 결과 객체 (RuleResult)
     */
    RuleResult evaluate(String fullText, String[] lines, Rule rule);
}
