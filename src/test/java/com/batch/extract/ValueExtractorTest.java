package com.batch.extract;

import com.batch.model.Rule;
import com.batch.model.StepMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [단위 테스트 (Unit Test) 학습 예제 - 텍스트 파싱 및 유틸리티 함수]
 * -------------------------------------------------------------------------------------
 * 💡 학습 포인트:
 * 1. 무상태(Stateless) 유틸리티 단위 테스트:
 *    - `ValueExtractor`는 내부 상태를 갖지 않는 순수 정적 메서드(Static Method) 모음입니다.
 *    - 동일한 입력에 대해 항상 동일한 출력을 반환하는 결정론적(Deterministic) 함수이므로,
 *      다양한 입력 조합과 엣지 케이스(null, 빈 문자열, 비정상 포맷)를 촘촘히 테스트하기에 가장 이상적입니다.
 * 2. 엣지 케이스 및 방어적 프로그래밍 검증:
 *    - `null` 입력 시 `NullPointerException`이 발생하지 않고 `null`을 반환하는지
 *    - 숫자가 없는 문자열 파싱 시 정상적으로 `null` 또는 `0`을 반환하는지 검증
 * 3. 정규식(Regex) 및 멀티라인(Multiline) 텍스트 패턴 매칭:
 *    - 윈도우(`\r\n`) 및 리눅스(`\n`) 개행 문자가 혼합된 환경에서도 공백과 개행을 정규화하여
 *      원하는 수치나 레이블을 정확히 추출하는지 검증
 * =====================================================================================
 */
@DisplayName("단위 테스트: ValueExtractor 정규식 매칭, 수치 변환, Step 메트릭 파싱")
public class ValueExtractorTest {
    
    /**
     * ---------------------------------------------------------------------------------
     * [단순 텍스트 출현 횟수 계산 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("countMatches: 단순 문자열('SUCCESS')의 출현 횟수(3회) 정확히 계산")
    public void testCountMatches_SimpleText() {
        // [Given] SUCCESS가 3번 포함된 텍스트
        String fullText = "SUCCESS occurred\nSUCCESS completed\nSUCCESS\n";
        Rule rule = new Rule("SEARCH", "SUCCESS", "COUNT_CHECK", "성공 카운트");

        // [When] 카운트 계산
        int count = ValueExtractor.countMatches(fullText, rule);

        // [Then]
        assertEquals(3, count, "SUCCESS가 3번 나타나야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [정규식 패턴 출현 횟수 계산 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("countMatches: 정규식 패턴('ERROR: \\d+')의 매칭 횟수(2회) 계산")
    public void testCountMatches_Regex() {
        // [Given] ERROR: 001, ERROR: 002 (2건)와 WARN: 003 (1건)이 있는 텍스트
        String fullText = "ERROR: 001\nERROR: 002\nWARN: 003\n";
        Rule rule = new Rule("SEARCH", "", "COUNT_CHECK", "에러 패턴 카운트");
        rule.regex = "ERROR: \\d+";

        // [When]
        int count = ValueExtractor.countMatches(fullText, rule);

        // [Then]
        assertEquals(2, count, "ERROR 패턴이 2번 나타나야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [DISPLAY 규칙: 기본 레이블 뒤의 값 추출]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("extractDisplayValue: 'Total Records: 100건'에서 '100건' 값 추출")
    public void testExtractDisplayValue_Simple() {
        // [Given]
        String fullText = "Total Records: 100건\nProcessed: 50건\n";
        String[] lines = fullText.split("\n");
        Rule rule = new Rule("DISPLAY", "Total Records", "EQUALS_N", "총 건수");

        // [When]
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);

        // [Then]
        assertEquals("100건", result, "100건이 추출되어야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [DISPLAY 규칙: 콜론 뒤의 공백이 있는 값 추출]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("extractDisplayValue: 'Skip Count: 0'에서 숫자 '0' 추출")
    public void testExtractDisplayValue_WithColon() {
        // [Given]
        String fullText = "Skip Count: 0\nWrite Count : 250\n";
        String[] lines = fullText.split("\n");
        Rule rule = new Rule("DISPLAY", "Skip Count", "EQUALS_0", "스킵 건수");

        // [When]
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);

        // [Then]
        assertEquals("0", result, "0이 추출되어야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [DISPLAY 규칙: 대상 키워드 미존재 시 null 반환]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("extractDisplayValue: 대상 키워드가 없으면 null 반환")
    public void testExtractDisplayValue_NotFound() {
        // [Given]
        String fullText = "Job Status: Running\nCompleted Date: 2024-09-01\n";
        String[] lines = fullText.split("\n");
        Rule rule = new Rule("DISPLAY", "Total Records", "COUNT_CHECK", "총 건수");

        // [When]
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);

        // [Then]
        assertNull(result, "패턴을 찾지 못하면 null 반환");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [DISPLAY 규칙: 등호(=) 구분자 뒤의 값 추출]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("extractDisplayValue: 등호 서식('Update Count=150')에서 '150' 추출")
    public void testExtractDisplayValue_WithRegex() {
        // [Given]
        String fullText = "Update Count=150\nDelete Count=25\n";
        String[] lines = fullText.split("\n");
        Rule rule = new Rule("DISPLAY", "", "COUNT_CHECK", "업데이트 건수");
        rule.regex = "Update Count";

        // [When]
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);

        // [Then]
        assertEquals("150", result, "150이 추출되어야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [숫자 파싱: 콤마(,) 및 단위('건')가 포함된 문자열 숫자 변환]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("parseNumber: 콤마 및 한글 단위('1,234,567건')를 Long 수치(1234567L)로 파싱")
    public void testParseNumber_WithComma() {
        Long result = ValueExtractor.parseNumber("1,234,567건");
        assertEquals(Long.valueOf(1234567), result, "1234567이 파싱되어야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [숫자 파싱: 단순 숫자]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("parseNumber: 단순 숫자 문자열('500')을 Long 수치(500L)로 파싱")
    public void testParseNumber_Simple() {
        Long result = ValueExtractor.parseNumber("500");
        assertEquals(Long.valueOf(500), result, "500이 파싱되어야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [숫자 파싱: 엣지 케이스 (null 입력)]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - Null Safety 검증: null이 입력되어도 예외를 던지지 않고 안전하게 null을 반환하는지 확인합니다.
     */
    @Test
    @DisplayName("parseNumber 엣지케이스: null 입력 시 안전하게 null 반환")
    public void testParseNumber_Null() {
        Long result = ValueExtractor.parseNumber(null);
        assertNull(result, "null 입력시 null 반환");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [숫자 파싱: 엣지 케이스 (숫자가 전혀 없는 문자열)]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("parseNumber 엣지케이스: 숫자가 없는 문자열('ERROR건') 입력 시 null 반환")
    public void testParseNumber_NoNumber() {
        Long result = ValueExtractor.parseNumber("ERROR건");
        assertNull(result, "숫자가 없으면 null 반환");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [Step 메트릭 파싱 검증]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - Spring Batch 로그 라인 배열에서 특정 StepName 섹션을 찾아
     *   Read, Write, Commit, Rollback 카운트를 정확히 추출하여 DTO로 매핑하는지 검증합니다.
     */
    @Test
    @DisplayName("parseStepMetrics: Spring Batch 로그에서 Step별 R/W/C/Rollback 카운트 매핑 검증")
    public void testParseStepMetrics() {
        // [Given] 모의 로그 라인 배열
        String[] lines = {
            "Step: DataLoadStep",
            "StepName : DataProcessStep",
            "ReadCount: 1,000",
            "WriteCount: 950",
            "CommitCount: 19",
            "RollbackCount: 0"
        };

        // [When] DataProcessStep 메트릭 파싱
        StepMetrics metrics = ValueExtractor.parseStepMetrics(lines, "DataProcessStep");

        // [Then]
        assertAll("StepMetrics 필드 매핑 검증",
            () -> assertNotNull(metrics, "메트릭 객체가 null이 아니어야 함"),
            () -> assertEquals(1000, metrics.readCount, "ReadCount 확인"),
            () -> assertEquals(950, metrics.writeCount, "WriteCount 확인"),
            () -> assertEquals(19, metrics.commitCount, "CommitCount 확인"),
            () -> assertEquals(0, metrics.rollbackCount, "RollbackCount 확인")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [Step 메트릭 파싱: 존재하지 않는 Step 이름 조회 시 null 반환]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("parseStepMetrics: 존재하지 않는 StepName 조회 시 null 반환")
    public void testParseStepMetrics_NotFound() {
        String[] lines = {
            "Step: DataLoadStep",
            "ReadCount: 1,000"
        };

        StepMetrics metrics = ValueExtractor.parseStepMetrics(lines, "NonExistentStep");
        assertNull(metrics, "존재하지 않는 Step은 null 반환");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [메트릭 단일 라인 수치 추출 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("extractMetricNumber: 'CommitCount: 123' 라인에서 숫자 123 추출")
    public void testExtractMetricNumber_Basic() {
        String line = "CommitCount: 123";
        long result = ValueExtractor.extractMetricNumber(line, "CommitCount");
        assertEquals(123L, result, "123이 추출되어야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [메트릭 단일 라인 수치 추출: 콤마 포함]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("extractMetricNumber: 'ReadCount: 1,234,567' 라인에서 1234567 추출")
    public void testExtractMetricNumber_WithComma() {
        String line = "ReadCount: 1,234,567";
        long result = ValueExtractor.extractMetricNumber(line, "ReadCount");
        assertEquals(1234567L, result, "1234567이 추출되어야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [메트릭 단일 라인 수치 추출: 키워드 불일치 시 0 반환]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("extractMetricNumber: 대상 키워드가 없으면 0 반환")
    public void testExtractMetricNumber_KeywordNotFound() {
        String line = "ProcessCount: 100";
        long result = ValueExtractor.extractMetricNumber(line, "ReadCount");
        assertEquals(0L, result, "키워드 없으면 0 반환");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [개행 문자가 포함된 멀티라인 텍스트 추출 검증]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 검색 키워드 자체가 여러 줄에 걸쳐 분할되어 있는 경우(예: "Validation Result\n(Result Details)"),
     *   정규식과 개행 무시 패턴을 통해 올바르게 매칭하고 후속 수치("500건")를 추출하는지 검증합니다.
     */
    @Test
    @DisplayName("extractDisplayValue 멀티라인: 개행이 포함된 복합 키워드 뒤의 수치 추출")
    public void testExtractDisplayValue_MultilineTarget() {
        // [Given] 개행이 포함된 복합 텍스트
        String fullText = "Validation Result\n(Result Details)\nTotal: 500건\n";
        String[] lines = fullText.split("\n");

        Rule rule = new Rule("DISPLAY", "Validation Result", "COUNT_CHECK", "검증 결과");
        rule.target = "Validation Result\n(Result Details)";

        // [When]
        String result = ValueExtractor.extractDisplayValue(fullText, lines, rule);

        // [Then]
        assertAll("멀티라인 패턴 매칭 단언",
            () -> assertNotNull(result, "멀티라인 패턴을 매칭해야 함"),
            () -> assertTrue(result.contains("500"), "500 건수가 추출되어야 함")
        );
    }
}
