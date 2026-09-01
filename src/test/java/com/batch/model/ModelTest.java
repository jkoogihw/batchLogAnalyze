package com.batch.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 데이터 모델 클래스 테스트
 */
public class ModelTest {
    
    /**
     * JobPolicy 생성 및 필드 확인
     */
    @Test
    public void testJobPolicy_Creation() {
        JobPolicy policy = new JobPolicy("01", "testJob", "Test Job Title", "test_");
        
        assertEquals("01", policy.jobNo, "jobNo 확인");
        assertEquals("testJob", policy.jobName, "jobName 확인");
        assertEquals("Test Job Title", policy.jobTitle, "jobTitle 확인");
        assertEquals("test_", policy.filePrefix, "filePrefix 확인");
        assertEquals(0, policy.rules.size(), "초기 규칙 목록은 비어있음");
    }
    
    /**
     * Rule 생성 및 필드 확인
     */
    @Test
    public void testRule_Creation() {
        Rule rule = new Rule("SEARCH", "SUCCESS", "EQUALS_0", "Success count");
        
        assertEquals("SEARCH", rule.type, "type 확인");
        assertEquals("SUCCESS", rule.target, "target 확인");
        assertEquals("EQUALS_0", rule.condition, "condition 확인");
        assertEquals("Success count", rule.description, "description 확인");
    }
    
    /**
     * RuleResult 생성 및 필드 확인
     */
    @Test
    public void testRuleResult_Creation() {
        RuleResult result = new RuleResult("Test rule", "SEARCH", true, "All OK");
        
        assertEquals("Test rule", result.description, "description 확인");
        assertEquals("SEARCH", result.type, "type 확인");
        assertTrue(result.passed, "passed 확인");
        assertEquals("All OK", result.message, "message 확인");
    }
    
    /**
     * CheckResult 생성 및 기본 상태
     */
    @Test
    public void testCheckResult_Creation() {
        CheckResult result = new CheckResult("01", "job1", "Job One");
        
        assertEquals("01", result.jobNo, "jobNo 확인");
        assertEquals("job1", result.jobName, "jobName 확인");
        assertEquals("Job One", result.jobTitle, "jobTitle 확인");
        assertTrue(result.overallPassed, "초기 통과 상태: true");
        assertFalse(result.fileFound, "초기 파일 존재: false");
    }
    
    /**
     * CheckResult에 규칙 결과 추가
     */
    @Test
    public void testCheckResult_AddRuleResult() {
        CheckResult result = new CheckResult("01", "job1", "Job One");
        
        RuleResult rule1 = new RuleResult("Rule 1", "SEARCH", true, "OK");
        result.addRuleResult(rule1);
        
        assertEquals(1, result.ruleResults.size(), "규칙 1개 추가됨");
        assertTrue(result.overallPassed, "통과 규칙만 추가됨: 통과 유지");
        
        RuleResult rule2 = new RuleResult("Rule 2", "DISPLAY", false, "Failed");
        result.addRuleResult(rule2);
        
        assertEquals(2, result.ruleResults.size(), "규칙 2개 추가됨");
        assertFalse(result.overallPassed, "실패 규칙 추가됨: 통과 상태 변경");
    }
    
    /**
     * StepMetrics 생성 및 디스플레이
     */
    @Test
    public void testStepMetrics_Display() {
        StepMetrics metrics = new StepMetrics("TestStep");
        metrics.readCount = 1000;
        metrics.writeCount = 950;
        metrics.commitCount = 19;
        metrics.rollbackCount = 0;
        
        assertEquals("TestStep", metrics.stepName, "Step 이름 확인");
        assertEquals(1000, metrics.readCount, "ReadCount 확인");
        assertEquals(950, metrics.writeCount, "WriteCount 확인");
        assertEquals(19, metrics.commitCount, "CommitCount 확인");
        assertEquals(0, metrics.rollbackCount, "RollbackCount 확인");
        
        String display = metrics.toDisplayString();
        assertTrue(display.contains("R:1000"), "디스플레이 문자열에 R이 포함");
        assertTrue(display.contains("Rollback:0"), "디스플레이 문자열에 Rollback이 포함");
    }
    
    /**
     * CheckResult의 비영업일 마킹
     */
    @Test
    public void testCheckResult_MarkAsHoliday() {
        CheckResult result = new CheckResult("01", "job1", "Job One");
        result.overallPassed = false;
        result.fileFound = false;
        
        result.markAsHoliday("Sunday");
        
        assertTrue(result.isHoliday, "비영업일 플래그 설정됨");
        assertEquals("Sunday", result.holidayDetail, "비영업일 상세정보 설정됨");
        assertTrue(result.overallPassed, "비영업일 표시 후: 통과 상태 변경");
    }
    
    /**
     * Rule의 toString
     */
    @Test
    public void testRule_ToString() {
        Rule rule = new Rule("DISPLAY", "Total Records", "EQUALS_N", "Count check");
        rule.expectedCount = 100;
        
        String str = rule.toString();
        
        assertTrue(str.contains("DISPLAY"), "Type이 포함됨");
        assertTrue(str.contains("Count check"), "Description이 포함됨");
    }
    
    /**
     * JobPolicy의 toString
     */
    @Test
    public void testJobPolicy_ToString() {
        JobPolicy policy = new JobPolicy("01", "job1", "Job One", "job_");
        policy.rules.add(new Rule("SEARCH", "SUCCESS", "COUNT_CHECK", "Rule 1"));
        policy.rules.add(new Rule("DISPLAY", "Count", "EQUALS_0", "Rule 2"));
        
        String str = policy.toString();
        
        assertTrue(str.contains("job1"), "Job이름이 포함됨");
        assertTrue(str.contains("2"), "규칙 개수가 포함됨");
    }
}
