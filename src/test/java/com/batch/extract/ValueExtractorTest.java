package com.batch.extract;

import com.batch.model.Rule;
import com.batch.model.StepMetrics;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ValueExtractor 테스트
 * 
 * 로그에서 값을 추출하는 다양한 시나리오를 테스트합니다.
 */
public class ValueExtractorTest {
    
    /**
     * SEARCH 규칙: 단순 텍스트 매칭 건수 계산
     */
    @Test
    public void testCountMatches_SimpleText() {
        String fullText = "SUCCESS occurred\nSUCCESS completed\nSUCCESS\n";
        Rule rule = new Rule("SEARCH", "SUCCESS", "COUNT_CHECK", "Success count");
        
        int count = ValueExtractor.countMatches(fullText, rule);
        assertEquals("SUCCESS가 3번 나타나야 함", 3, count);
    }
    
    /**
     * SEARCH 규칙: Regex 패턴 매칭
     */
    @Test
    public void testCountMatches_Regex() {
        String fullText = "ERROR: 001\nERROR: 002\nWARN: 003\n";
        Rule rule = new Rule("SEARCH", "", "COUNT_CHECK", "Error count");
        rule.regex = "ERROR: \\d+";
        
        int count = ValueExtractor.countMatches(fullText, rule);
        assertEquals("ERROR 패턴이 2번 나타나야 함", 2, count);
    }
    
    /**
     * DISPLAY 규칙: 간단한 값 추출
     */
    @Test
    public void testExtractDisplayValue_Simple() {
        String fullText = "Total Records: 100건\nProcessed: 50건\n";
        String[] lines = fullText.split("\n");
        
        Rule rule = new Rule("DISPLAY", "Total Records", "EQUALS_N", "Total count");
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);
        
        assertEquals("100건이 추출되어야 함", "100건", result);
    }
    
    /**
     * DISPLAY 규칙: 콜론 뒤의 값 추출
     */
    @Test
    public void testExtractDisplayValue_WithColon() {
        String fullText = "Skip Count: 0\nWrite Count : 250\n";
        String[] lines = fullText.split("\n");
        
        Rule rule = new Rule("DISPLAY", "Skip Count", "EQUALS_0", "Skip count");
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);
        
        assertEquals("0이 추출되어야 함", "0", result);
    }
    
    /**
     * DISPLAY 규칙: 값이 없는 경우
     */
    @Test
    public void testExtractDisplayValue_NotFound() {
        String fullText = "Job Status: Running\nCompleted Date: 2024-09-01\n";
        String[] lines = fullText.split("\n");
        
        Rule rule = new Rule("DISPLAY", "Total Records", "COUNT_CHECK", "Total");
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);
        
        assertNull("패턴을 찾지 못하면 null 반환", result);
    }
    
    /**
     * DISPLAY 규칙: Regex 패턴 사용
     */
    @Test
    public void testExtractDisplayValue_WithRegex() {
        String fullText = "Update Count=150\nDelete Count=25\n";
        String[] lines = fullText.split("\n");
        
        Rule rule = new Rule("DISPLAY", "", "COUNT_CHECK", "Update count");
        rule.regex = "Update Count";
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);
        
        assertEquals("150이 추출되어야 함", "150", result);
    }
    
    /**
     * 숫자 추출: 쉼표가 포함된 수
     */
    @Test
    public void testParseNumber_WithComma() {
        Long result = ValueExtractor.parseNumber("1,234,567건");
        assertEquals("1234567이 파싱되어야 함", Long.valueOf(1234567), result);
    }
    
    /**
     * 숫자 추출: 단순 숫자
     */
    @Test
    public void testParseNumber_Simple() {
        Long result = ValueExtractor.parseNumber("500");
        assertEquals("500이 파싱되어야 함", Long.valueOf(500), result);
    }
    
    /**
     * 숫자 추출: null 입력
     */
    @Test
    public void testParseNumber_Null() {
        Long result = ValueExtractor.parseNumber(null);
        assertNull("null 입력시 null 반환", result);
    }
    
    /**
     * 숫자 추출: 숫자가 없는 경우
     */
    @Test
    public void testParseNumber_NoNumber() {
        Long result = ValueExtractor.parseNumber("ERROR건");
        assertNull("숫자가 없으면 null 반환", result);
    }
    
    /**
     * Step 메트릭 파싱
     */
    @Test
    public void testParseStepMetrics() {
        String[] lines = {
            "Step: DataLoadStep",
            "StepName : DataProcessStep",
            "ReadCount: 1,000",
            "WriteCount: 950",
            "CommitCount: 19",
            "RollbackCount: 0"
        };
        
        StepMetrics metrics = ValueExtractor.parseStepMetrics(lines, "DataProcessStep");
        
        assertNotNull("메트릭이 파싱되어야 함", metrics);
        assertEquals("ReadCount 확인", 1000, metrics.readCount);
        assertEquals("WriteCount 확인", 950, metrics.writeCount);
        assertEquals("CommitCount 확인", 19, metrics.commitCount);
        assertEquals("RollbackCount 확인", 0, metrics.rollbackCount);
    }
    
    /**
     * Step 메트릭: 존재하지 않는 Step
     */
    @Test
    public void testParseStepMetrics_NotFound() {
        String[] lines = {
            "Step: DataLoadStep",
            "ReadCount: 1,000"
        };
        
        StepMetrics metrics = ValueExtractor.parseStepMetrics(lines, "NonExistentStep");
        
        assertNull("존재하지 않는 Step은 null 반환", metrics);
    }
    
    /**
     * 메트릭 숫자 추출: 기본 형식
     */
    @Test
    public void testExtractMetricNumber_Basic() {
        String line = "CommitCount: 123";
        long result = ValueExtractor.extractMetricNumber(line, "CommitCount");
        
        assertEquals("123이 추출되어야 함", 123L, result);
    }
    
    /**
     * 메트릭 숫자 추출: 쉼표 포함
     */
    @Test
    public void testExtractMetricNumber_WithComma() {
        String line = "ReadCount: 1,234,567";
        long result = ValueExtractor.extractMetricNumber(line, "ReadCount");
        
        assertEquals("1234567이 추출되어야 함", 1234567L, result);
    }
    
    /**
     * 메트릭 숫자 추출: 키워드 없음
     */
    @Test
    public void testExtractMetricNumber_KeywordNotFound() {
        String line = "ProcessCount: 100";
        long result = ValueExtractor.extractMetricNumber(line, "ReadCount");
        
        assertEquals("키워드 없으면 0 반환", 0L, result);
    }
    
    /**
     * 멀티라인 텍스트 추출 (개행 포함)
     */
    @Test
    public void testExtractDisplayValue_MultilineTarget() {
        String fullText = "Validation Result\n(Result Details)\nTotal: 500건\n";
        String[] lines = fullText.split("\n");
        
        Rule rule = new Rule("DISPLAY", "Validation Result", "COUNT_CHECK", "Validation");
        rule.target = "Validation Result\n(Result Details)";
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);
        
        // 멀티라인 패턴 매칭 테스트
        assertNotNull("멀티라인 패턴을 매칭해야 함", result);
    }
}
