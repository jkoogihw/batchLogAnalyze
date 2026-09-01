package com.batch.analyzer.evaluator;

import com.batch.model.Rule;
import com.batch.model.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [단위 테스트: 룰 평가기 레지스트리 및 전략 패턴(Strategy Pattern) 검증]
 * -------------------------------------------------------------------------------------
 * 💡 학습 포인트:
 * 1. OCP (개방-폐쇄 원칙) 검증:
 *    - 신규 사용자 정의 평가기(CustomRuleEvaluator)를 등록하고, 기존 코드 수정 없이
 *      새로운 룰 타입("CUSTOM_CHECK")이 정상 라우팅되어 처리되는지 확인합니다.
 * 2. Fallback 안전성 검증:
 *    - 미지원 룰 타입이 들어왔을 때 NPE 없이 안내 메시지를 포함한 실패 결과를 생성하는지 확인합니다.
 * =====================================================================================
 */
@DisplayName("단위 테스트: RuleEvaluatorRegistry 및 전략 패턴(Strategy Pattern)")
public class RuleEvaluatorRegistryTest {

    private RuleEvaluatorRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new RuleEvaluatorRegistry();
    }

    @Test
    @DisplayName("기본 전략 로딩: SEARCH, DISPLAY, STEP_METRICS 3대 평가기 자동 등록 확인")
    public void testDefaultEvaluatorsRegistered() {
        assertNotNull(registry.getEvaluator("SEARCH"), "SEARCH 평가기 등록 확인");
        assertNotNull(registry.getEvaluator("DISPLAY"), "DISPLAY 평가기 등록 확인");
        assertNotNull(registry.getEvaluator("STEP_METRICS"), "STEP_METRICS 평가기 등록 확인");
        assertEquals(3, registry.getEvaluators().size(), "기본 3개 등록");
    }

    @Test
    @DisplayName("OCP 확장성 검증: 신규 사용자 정의 RuleEvaluator 등록 및 실행")
    public void testCustomRuleEvaluatorRegistration() {
        // [Given] 신규 사용자 정의 평가기 생성
        RuleEvaluator customEvaluator = new RuleEvaluator() {
            @Override
            public boolean supports(String ruleType) {
                return "CUSTOM_CHECK".equalsIgnoreCase(ruleType);
            }

            @Override
            public RuleResult evaluate(String fullText, String[] lines, Rule rule) {
                RuleResult rr = new RuleResult();
                rr.type = "CUSTOM_CHECK";
                rr.passed = true;
                rr.extractedValue = "커스텀값";
                rr.message = "사용자 정의 검증 성공";
                return rr;
            }
        };

        // [When] 신규 평가기 등록
        registry.register(customEvaluator);

        // [Then] 신규 평가기 검색 및 평가 실행 확인
        RuleEvaluator resolved = registry.getEvaluator("CUSTOM_CHECK");
        assertNotNull(resolved, "신규 평가기가 조회되어야 함");

        Rule customRule = new Rule("CUSTOM_CHECK", "", "COUNT", "커스텀 검증");
        RuleResult result = registry.evaluate("sample log", new String[]{"sample log"}, customRule);

        assertAll("신규 룰 평가 결과 검증",
            () -> assertTrue(result.passed, "사용자 정의 룰 통과"),
            () -> assertEquals("커스텀값", result.extractedValue),
            () -> assertEquals("사용자 정의 검증 성공", result.message)
        );
    }

    @Test
    @DisplayName("미지원 룰 타입 처리: 지원하지 않는 룰 타입 입력 시 안전한 Fallback 결과 반환")
    public void testUnsupportedRuleTypeFallback() {
        Rule unsupportedRule = new Rule("UNKNOWN_TYPE", "target", "cond", "미지원 룰");

        RuleResult result = registry.evaluate("log", new String[]{"log"}, unsupportedRule);

        assertAll("Fallback 결과 검증",
            () -> assertFalse(result.passed, "미지원 룰은 passed=false"),
            () -> assertEquals("미지원 룰 타입", result.extractedValue),
            () -> assertTrue(result.message.contains("지원하지 않는 룰 타입"))
        );
    }
}
