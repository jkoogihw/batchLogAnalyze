package com.batch.analyzer;

import com.batch.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogAnalyzer 테스트
 * 
 * 로그 분석 로직을 테스트합니다.
 */
public class LogAnalyzerTest {
    
    /**
     * SEARCH 규칙 평가: 성공 케이스
     */
    @Test
    public void testEvaluateSearchRule_EQUALS_0_Success() {
        String logText = "Job started\nJob completed\n";
        String[] lines = logText.split("\n");
        
        Rule rule = new Rule("SEARCH", "ERROR", "EQUALS_0", "No errors");
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);
        
        assertTrue(result.passed, "ERROR가 0개이므로 통과해야 함");
        assertEquals("0건", result.extractedValue, "추출값이 0건이어야 함");
    }
    
    /**
     * SEARCH 규칙 평가: 실패 케이스
     */
    @Test
    public void testEvaluateSearchRule_EQUALS_0_Fail() {
        String logText = "ERROR occurred\nERROR happened\nJob completed\n";
        String[] lines = logText.split("\n");
        
        Rule rule = new Rule("SEARCH", "ERROR", "EQUALS_0", "No errors");
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);
        
        assertFalse(result.passed, "ERROR가 2개이므로 실패해야 함");
        assertEquals("2건", result.extractedValue, "추출값이 2건이어야 함");
    }
    
    /**
     * SEARCH 규칙 평가: EQUALS_N 조건
     */
    @Test
    public void testEvaluateSearchRule_EQUALS_N() {
        String logText = "SUCCESS\nSUCCESS\nSUCCESS\n";
        String[] lines = logText.split("\n");
        
        Rule rule = new Rule("SEARCH", "SUCCESS", "EQUALS_N", "Success count");
        rule.expectedCount = 3;
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);
        
        assertTrue(result.passed, "SUCCESS가 정확히 3개이므로 통과해야 함");
        assertEquals("3건", result.extractedValue, "기대값이 달성되었음");
    }
    
    /**
     * DISPLAY 규칙 평가: EQUALS_0
     */
    @Test
    public void testEvaluateDisplayRule_EQUALS_0_Success() {
        String logText = "Skip Count: 0\nOther info\n";
        String[] lines = logText.split("\n");
        
        Rule rule = new Rule("DISPLAY", "Skip Count", "EQUALS_0", "No skips");
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);
        
        assertTrue(result.passed, "Skip Count가 0이므로 통과해야 함");
    }
    
    /**
     * DISPLAY 규칙 평가: ERROR_IF_PRESENT
     */
    @Test
    public void testEvaluateDisplayRule_ERROR_IF_PRESENT_Success() {
        String logText = "Warning Count: 0\nProcessing...\n";
        String[] lines = logText.split("\n");
        
        Rule rule = new Rule("DISPLAY", "Warning Count", "ERROR_IF_PRESENT", "No warnings");
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);
        
        assertTrue(result.passed, "Warning이 0이므로 통과해야 함");
    }
    
    /**
     * DISPLAY 규칙 평가: 패턴 미발견
     */
    @Test
    public void testEvaluateDisplayRule_NotFound() {
        String logText = "Job Status: Running\nDate: 2024-09-01\n";
        String[] lines = logText.split("\n");
        
        Rule rule = new Rule("DISPLAY", "Total Records", "COUNT_CHECK", "Total count");
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);
        
        assertFalse(result.passed, "패턴을 찾지 못했으므로 실패해야 함");
        assertEquals("미발견", result.extractedValue, "미발견 상태 확인");
    }
    
    /**
     * STEP_METRICS 규칙 평가: ROLLBACK_ZERO
     */
    @Test
    public void testEvaluateStepMetricsRule_ROLLBACK_ZERO_Success() {
        String[] lines = {
            "Step: DataLoadStep",
            "StepName : ProcessStep",
            "ReadCount: 1000",
            "WriteCount: 950",
            "CommitCount: 19",
            "RollbackCount: 0"
        };
        
        Rule rule = new Rule("STEP_METRICS", "", "ROLLBACK_ZERO", "Rollback count");
        rule.stepName = "ProcessStep";
        RuleResult result = LogAnalyzer.evaluateRule("", lines, rule);
        
        assertTrue(result.passed, "Rollback이 0이므로 통과해야 함");
        assertTrue(result.extractedValue.contains("Rollback"), "메트릭 정보가 포함되어야 함");
    }
    
    /**
     * STEP_METRICS 규칙 평가: Rollback 발생
     */
    @Test
    public void testEvaluateStepMetricsRule_ROLLBACK_Fail() {
        String[] lines = {
            "StepName : FailStep",
            "ReadCount: 100",
            "WriteCount: 50",
            "CommitCount: 5",
            "RollbackCount: 3"
        };
        
        Rule rule = new Rule("STEP_METRICS", "", "ROLLBACK_ZERO", "Rollback check");
        rule.stepName = "FailStep";
        RuleResult result = LogAnalyzer.evaluateRule("", lines, rule);
        
        assertFalse(result.passed, "Rollback이 3이므로 실패해야 함");
        assertTrue(result.message.contains("3"), "실패 메시지에 롤백 수가 포함되어야 함");
    }
    
    /**
     * CheckResult에 RuleResult 추가
     */
    @Test
    public void testCheckResult_AddRuleResult() {
        CheckResult cr = new CheckResult("01", "job1", "Job One");
        assertTrue(cr.overallPassed, "초기 상태: 통과");
        
        RuleResult pass = new RuleResult("Test 1", "SEARCH", true, "OK");
        cr.addRuleResult(pass);
        assertTrue(cr.overallPassed, "통과 규칙 추가 후: 통과");
        
        RuleResult fail = new RuleResult("Test 2", "DISPLAY", false, "Failed");
        cr.addRuleResult(fail);
        assertFalse(cr.overallPassed, "실패 규칙 추가 후: 실패");
        
        assertEquals(2, cr.ruleResults.size(), "규칙 2개 추가됨");
    }
    
    /**
     * 비영업일 표시
     */
    @Test
    public void testCheckResult_MarkAsHoliday() {
        CheckResult cr = new CheckResult("01", "job1", "Job One");
        cr.overallPassed = false; // 초기: 실패 상태
        
        cr.markAsHoliday("Sunday");
        
        assertTrue(cr.overallPassed, "비영업일 표시 후: 통과");
        assertTrue(cr.isHoliday, "비영업일 플래그 설정됨");
        assertEquals("Sunday", cr.holidayDetail, "비영업일 상세정보 저장됨");
    }
    
    /**
     * 정규식을 사용한 SEARCH
     */
    @Test
    public void testEvaluateSearchRule_WithRegex() {
        String logText = "ERROR: 001\nWARN: 002\nERROR: 003\n";
        String[] lines = logText.split("\n");
        
        Rule rule = new Rule("SEARCH", "", "EQUALS_N", "Error count");
        rule.regex = "ERROR:\\s*\\d+";
        rule.expectedCount = 2;
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);
        
        assertTrue(result.passed, "ERROR 패턴 2개 일치");
        assertEquals("2건", result.extractedValue, "추출값이 2건이어야 함");
    }
    
    /**
     * 콤마를 포함한 숫자 추출
     */
    @Test
    public void testEvaluateDisplayRule_WithComma() {
        String logText = "Total Processed: 1,234,567\nStatus: OK\n";
        String[] lines = logText.split("\n");
        
        Rule rule = new Rule("DISPLAY", "Total Processed", "COUNT_CHECK", "Total");
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);
        
        assertTrue(result.passed, "처리 성공");
        assertEquals("1,234,567", result.extractedValue, "콤마를 포함한 숫자 추출");
    }
}
