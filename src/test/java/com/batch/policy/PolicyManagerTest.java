package com.batch.policy;

import com.batch.model.JobPolicy;
import com.batch.model.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * PolicyManager 테스트
 * 
 * JSON 정책 파일 파싱을 테스트합니다.
 */
public class PolicyManagerTest {
    
    /**
     * 간단한 JSON 정책 파싱
     */
    @Test
    public void testParseJsonPolicies_Basic() {
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"testJob001\", " +
                "\"jobTitle\": \"Test Job One\", \"filePrefix\": \"test_\", " +
                "\"rules\": [{\"type\": \"SEARCH\", \"target\": \"SUCCESS\", " +
                "\"condition\": \"COUNT_CHECK\", \"description\": \"Success count\"}]}]";
        
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        
        assertEquals("정책 1개가 파싱되어야 함", 1, policies.size());
        JobPolicy job = policies.get(0);
        assertEquals("Job 번호 확인", "01", job.jobNo);
        assertEquals("Job 이름 확인", "testJob001", job.jobName);
        assertEquals("파일 접두사 확인", "test_", job.filePrefix);
        assertEquals("규칙 1개 확인", 1, job.rules.size());
    }
    
    /**
     * 여러 정책 파싱
     */
    @Test
    public void testParseJsonPolicies_Multiple() {
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", \"rules\": []}, " +
                "{\"jobNo\": \"02\", \"jobName\": \"job2\", " +
                "\"jobTitle\": \"Job Two\", \"filePrefix\": \"job2_\", \"rules\": []}]";
        
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        
        assertEquals("정책 2개가 파싱되어야 함", 2, policies.size());
    }
    
    /**
     * SEARCH 규칙 파싱
     */
    @Test
    public void testParseJsonPolicies_SearchRule() {
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"SEARCH\", \"target\": \"ERROR\", " +
                "\"condition\": \"EQUALS_0\", \"description\": \"No errors\"}]}]";
        
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        Rule rule = policies.get(0).rules.get(0);
        
        assertEquals("규칙 타입 확인", "SEARCH", rule.type);
        assertEquals("검색 대상 확인", "ERROR", rule.target);
        assertEquals("조건 확인", "EQUALS_0", rule.condition);
    }
    
    /**
     * DISPLAY 규칙 파싱
     */
    @Test
    public void testParseJsonPolicies_DisplayRule() {
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"DISPLAY\", \"target\": \"Total Records\", " +
                "\"condition\": \"EQUALS_N\", \"expectedCount\": 100, " +
                "\"description\": \"Record count\"}]}]";
        
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        Rule rule = policies.get(0).rules.get(0);
        
        assertEquals("규칙 타입 확인", "DISPLAY", rule.type);
        assertEquals("기대값 확인", 100, rule.expectedCount);
    }
    
    /**
     * STEP_METRICS 규칙 파싱
     */
    @Test
    public void testParseJsonPolicies_StepMetricsRule() {
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"STEP_METRICS\", \"stepName\": \"ProcessStep\", " +
                "\"condition\": \"ROLLBACK_ZERO\", \"description\": \"Rollback count\"}]}]";
        
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        Rule rule = policies.get(0).rules.get(0);
        
        assertEquals("규칙 타입 확인", "STEP_METRICS", rule.type);
        assertEquals("Step 이름 확인", "ProcessStep", rule.stepName);
    }
    
    /**
     * 비영업일 설정 파싱
     */
    @Test
    public void testParseJsonPolicies_HolidayPattern() {
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"holidayCheck\": {\"pattern\": \"Saturday|Sunday\"}, " +
                "\"rules\": []}]";
        
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        JobPolicy job = policies.get(0);
        
        assertNotNull("비영업일 패턴이 파싱되어야 함", job.holidayPattern);
        assertTrue("패턴에 Saturday가 포함되어야 함", 
                job.holidayPattern.contains("Saturday"));
    }
    
    /**
     * Regex 필드 파싱
     */
    @Test
    public void testParseJsonPolicies_RegexField() {
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"SEARCH\", \"regex\": \"ERROR:\\\\\\\\s*\\\\\\\\d+\", " +
                "\"condition\": \"EQUALS_0\", \"description\": \"Error pattern\"}]}]";
        
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        Rule rule = policies.get(0).rules.get(0);
        
        assertNotNull("Regex가 파싱되어야 함", rule.regex);
        assertTrue("역슬래시가 처리되어야 함", rule.regex.contains("\\"));
    }
    
    /**
     * 여러 규칙 파싱
     */
    @Test
    public void testParseJsonPolicies_MultipleRules() {
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"SEARCH\", \"target\": \"SUCCESS\", " +
                "\"condition\": \"COUNT_CHECK\", \"description\": \"Rule 1\"}, " +
                "{\"type\": \"DISPLAY\", \"target\": \"Total\", " +
                "\"condition\": \"EQUALS_N\", \"expectedCount\": 100, " +
                "\"description\": \"Rule 2\"}]}]";
        
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        JobPolicy job = policies.get(0);
        
        assertEquals("규칙 2개가 파싱되어야 함", 2, job.rules.size());
        assertEquals("첫 번째 규칙 타입", "SEARCH", job.rules.get(0).type);
        assertEquals("두 번째 규칙 타입", "DISPLAY", job.rules.get(1).type);
    }
    
    /**
     * 빈 정책 배열
     */
    @Test
    public void testParseJsonPolicies_Empty() {
        String json = "[]";
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        
        assertEquals("빈 배열 파싱", 0, policies.size());
    }
}
