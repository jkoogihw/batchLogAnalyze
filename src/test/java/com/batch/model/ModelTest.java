package com.batch.model;

import org.junit.Test;

import static org.junit.Assert.*;

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
        
        assertEquals("jobNo 확인", "01", policy.jobNo);
        assertEquals("jobName 확인", "testJob", policy.jobName);
        assertEquals("jobTitle 확인", "Test Job Title", policy.jobTitle);
        assertEquals("filePrefix 확인", "test_", policy.filePrefix);
        assertEquals("초기 규칙 목록은 비어있음", 0, policy.rules.size());
    }
    
    /**
     * Rule 생성 및 필드 확인
     */
    @Test
    public void testRule_Creation() {
        Rule rule = new Rule("SEARCH", "SUCCESS", "EQUALS_0", "Success count");
        
        assertEquals("type 확인", "SEARCH", rule.type);
        assertEquals("target 확인", "SUCCESS", rule.target);
        assertEquals("condition 확인", "EQUALS_0", rule.condition);
        assertEquals("description 확인", "Success count", rule.description);
    }
    
    /**
     * RuleResult 생성 및 필드 확인
     */
    @Test
    public void testRuleResult_Creation() {
        RuleResult result = new RuleResult("Test rule", "SEARCH", true, "All OK");
        
        assertEquals("description 확인", "Test rule", result.description);
        assertEquals("type 확인", "SEARCH", result.type);
        assertTrue("passed 확인", result.passed);
        assertEquals("message 확인", "All OK", result.message);
    }
    
    /**
     * CheckResult 생성 및 기본 상태
     */
    @Test
    public void testCheckResult_Creation() {
        CheckResult result = new CheckResult("01", "job1", "Job One");
        
        assertEquals("jobNo 확인", "01", result.jobNo);
        assertEquals("jobName 확인", "job1", result.jobName);
        assertEquals("jobTitle 확인", "Job One", result.jobTitle);
        assertTrue("초기 통과 상태: true", result.overallPassed);
        assertFalse("초기 파일 존재: false", result.fileFound);
    }
    
    /**
     * CheckResult에 규칙 결과 추가
     */
    @Test
    public void testCheckResult_AddRuleResult() {
        CheckResult result = new CheckResult("01", "job1", "Job One");
        
        RuleResult rule1 = new RuleResult("Rule 1", "SEARCH", true, "OK");
        result.addRuleResult(rule1);
        
        assertEquals("규칙 1개 추가됨", 1, result.ruleResults.size());
        assertTrue("통과 규칙만 추가됨: 통과 유지", result.overallPassed);
        
        RuleResult rule2 = new RuleResult("Rule 2", "DISPLAY", false, "Failed");
        result.addRuleResult(rule2);
        
        assertEquals("규칙 2개 추가됨", 2, result.ruleResults.size());
        assertFalse("실패 규칙 추가됨: 통과 상태 변경", result.overallPassed);
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
        
        assertEquals("Step 이름 확인", "TestStep", metrics.stepName);
        assertEquals("ReadCount 확인", 1000, metrics.readCount);
        assertEquals("WriteCount 확인", 950, metrics.writeCount);
        assertEquals("CommitCount 확인", 19, metrics.commitCount);
        assertEquals("RollbackCount 확인", 0, metrics.rollbackCount);
        
        String display = metrics.toDisplayString();
        assertTrue("디스플레이 문자열에 R이 포함", display.contains("R:1000"));
        assertTrue("디스플레이 문자열에 Rollback이 포함", display.contains("Rollback:0"));
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
        
        assertTrue("비영업일 플래그 설정됨", result.isHoliday);
        assertEquals("비영업일 상세정보 설정됨", "Sunday", result.holidayDetail);
        assertTrue("비영업일 표시 후: 통과 상태 변경", result.overallPassed);
    }
    
    /**
     * Rule의 toString
     */
    @Test
    public void testRule_ToString() {
        Rule rule = new Rule("DISPLAY", "Total Records", "EQUALS_N", "Count check");
        rule.expectedCount = 100;
        
        String str = rule.toString();
        
        assertTrue("Type이 포함됨", str.contains("DISPLAY"));
        assertTrue("Description이 포함됨", str.contains("Count check"));
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
        
        assertTrue("Job이름이 포함됨", str.contains("job1"));
        assertTrue("규칙 개수가 포함됨", str.contains("2"));
    }
}
