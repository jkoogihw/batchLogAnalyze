package com.batch.analyzer;

import com.batch.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [단위 테스트 (Unit Test) 학습 예제 - 핵심 분석 엔진]
 * -------------------------------------------------------------------------------------
 * 💡 학습 포인트:
 * 1. 순수 단위 테스트 (Pure Unit Test):
 *    - 디스크 파일 I/O나 데이터베이스 연결 없이, 메모리 상의 문자열(`logText`, `lines`)과
 *      도메인 객체(`Rule`)만을 입력으로 전달하여 순수 로직의 정확성을 초고속(수 밀리초)으로 검증합니다.
 * 2. 긍정 테스트(Positive)와 부정 테스트(Negative):
 *    - 정상적인 통과 케이스(0건 만족, N건 일치)뿐만 아니라, 오류 발생 케이스(0건이어야 하는데 2건 발생)를
 *      반드시 함께 작성하여 경계 조건(Boundary Condition)을 빈틈없이 방어합니다.
 * 3. 룰 타입별 독립 검증:
 *    - SEARCH (문자열/정규식 검색 횟수)
 *    - DISPLAY (레이블 뒤의 값 추출 및 조건 비교)
 *    - STEP_METRICS (Spring Batch Step의 RollbackCount 0건 여부)
 *    - HOLIDAY (비영업일 예외 안내 메시지 처리)
 * =====================================================================================
 */
@DisplayName("단위 테스트: LogAnalyzer 핵심 룰 평가 및 도메인 분석 엔진")
public class LogAnalyzerTest {
    
    /**
     * ---------------------------------------------------------------------------------
     * [SEARCH 규칙 단위 테스트 - 긍정 케이스]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - `EQUALS_0` 조건은 해당 단어("ERROR")가 로그에 0번 나타나야 PASS입니다.
     */
    @Test
    @DisplayName("SEARCH 규칙 - EQUALS_0 조건: 오류 키워드가 0건일 때 통과(PASS)")
    public void testEvaluateSearchRule_EQUALS_0_Success() {
        // [Given] ERROR 키워드가 없는 정상 로그 텍스트
        String logText = "Job started\nJob completed successfully\n";
        String[] lines = logText.split("\n");
        Rule rule = new Rule("SEARCH", "ERROR", "EQUALS_0", "오류 미발생 검증");

        // [When] 룰 평가 실행
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);

        // [Then] PASS 및 0건 추출 단언
        assertAll("SEARCH EQUALS_0 성공 검증",
            () -> assertTrue(result.passed, "ERROR가 0개이므로 통과해야 함"),
            () -> assertEquals("0건", result.extractedValue, "추출값이 0건이어야 함")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [SEARCH 규칙 단위 테스트 - 부정(실패) 케이스]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - `EQUALS_0` 조건에서 오류 키워드가 2건 발견되면 `result.passed == false`가 되어야 합니다.
     */
    @Test
    @DisplayName("SEARCH 규칙 - EQUALS_0 조건: 오류 키워드가 2건 감지되면 실패(FAIL)")
    public void testEvaluateSearchRule_EQUALS_0_Fail() {
        // [Given] ERROR 키워드가 2번 포함된 실패 로그 텍스트
        String logText = "ERROR occurred during step 1\nERROR happened in step 2\nJob aborted\n";
        String[] lines = logText.split("\n");
        Rule rule = new Rule("SEARCH", "ERROR", "EQUALS_0", "오류 미발생 검증");

        // [When] 룰 평가 실행
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);

        // [Then] FAIL 및 2건 추출 단언
        assertAll("SEARCH EQUALS_0 실패 검증",
            () -> assertFalse(result.passed, "ERROR가 2개이므로 실패(FAIL)여야 함"),
            () -> assertEquals("2건", result.extractedValue, "추출값이 2건이어야 함")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [SEARCH 규칙 단위 테스트 - 특정 수치 일치 (EQUALS_N)]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("SEARCH 규칙 - EQUALS_N 조건: 기대 건수(3건)와 정확히 일치할 때 통과")
    public void testEvaluateSearchRule_EQUALS_N() {
        // [Given] SUCCESS가 3번 나타나는 로그 텍스트
        String logText = "SUCCESS\nSUCCESS\nSUCCESS\n";
        String[] lines = logText.split("\n");
        Rule rule = new Rule("SEARCH", "SUCCESS", "EQUALS_N", "성공 횟수 3건 일치 검증");
        rule.expectedCount = 3;

        // [When] 룰 평가 실행
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);

        // [Then] 기대값 일치 확인
        assertAll("SEARCH EQUALS_N 검증",
            () -> assertTrue(result.passed, "SUCCESS가 정확히 3개이므로 통과해야 함"),
            () -> assertEquals("3건", result.extractedValue, "추출값이 3건이어야 함")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [DISPLAY 규칙 단위 테스트 - 0건 체크]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("DISPLAY 규칙 - EQUALS_0 조건: Skip Count: 0 정상 추출 및 통과")
    public void testEvaluateDisplayRule_EQUALS_0_Success() {
        // [Given] Skip Count: 0 형태의 라인이 포함된 로그
        String logText = "Skip Count: 0\nOther batch info\n";
        String[] lines = logText.split("\n");
        Rule rule = new Rule("DISPLAY", "Skip Count", "EQUALS_0", "스킵 0건 확인");

        // [When]
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);

        // [Then]
        assertTrue(result.passed, "Skip Count가 0이므로 통과해야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [DISPLAY 규칙 단위 테스트 - 에러 존재 여부 체크 (ERROR_IF_PRESENT)]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("DISPLAY 규칙 - ERROR_IF_PRESENT 조건: Warning이 0건이면 정상 통과")
    public void testEvaluateDisplayRule_ERROR_IF_PRESENT_Success() {
        // [Given]
        String logText = "Warning Count: 0\nProcessing...\n";
        String[] lines = logText.split("\n");
        Rule rule = new Rule("DISPLAY", "Warning Count", "ERROR_IF_PRESENT", "경고 미발생 확인");

        // [When]
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);

        // [Then]
        assertTrue(result.passed, "Warning이 0이므로 통과해야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [DISPLAY 규칙 단위 테스트 - 대상 미발견 시 처리]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("DISPLAY 규칙 - 패턴 미발견: 로그 내 대상 키워드가 없으면 FAIL 처리")
    public void testEvaluateDisplayRule_NotFound() {
        // [Given] Total Records 키워드가 없는 로그
        String logText = "Job Status: Running\nDate: 2024-09-01\n";
        String[] lines = logText.split("\n");
        Rule rule = new Rule("DISPLAY", "Total Records", "COUNT_CHECK", "건수 확인");

        // [When]
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);

        // [Then]
        assertAll("미발견 상태 단언",
            () -> assertFalse(result.passed, "패턴을 찾지 못했으므로 실패(false)여야 함"),
            () -> assertEquals("미발견", result.extractedValue, "추출값에 '미발견' 기록")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [STEP_METRICS 규칙 단위 테스트 - Rollback 0건 판정]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - Spring Batch 실행 로그 형태(`ReadCount`, `WriteCount`, `CommitCount`, `RollbackCount`)를
     *   모의(Mocking) 라인 배열로 구성하여 파싱을 검증합니다.
     */
    @Test
    @DisplayName("STEP_METRICS 규칙 - ROLLBACK_ZERO 조건: RollbackCount가 0일 때 통과")
    public void testEvaluateStepMetricsRule_ROLLBACK_ZERO_Success() {
        // [Given] Spring Batch 표준 로그 라인들
        String[] lines = {
            "Step: DataLoadStep",
            "StepName : ProcessStep",
            "ReadCount: 1000",
            "WriteCount: 950",
            "CommitCount: 19",
            "RollbackCount: 0"
        };
        Rule rule = new Rule("STEP_METRICS", "", "ROLLBACK_ZERO", "롤백 미발생 검증");
        rule.stepName = "ProcessStep";

        // [When]
        RuleResult result = LogAnalyzer.evaluateRule("", lines, rule);

        // [Then]
        assertAll("Step 메트릭 0 롤백 검증",
            () -> assertTrue(result.passed, "Rollback이 0이므로 통과해야 함"),
            () -> assertTrue(result.extractedValue.contains("Rollback:0"), "메트릭 문자열에 Rollback:0 포함")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [STEP_METRICS 규칙 단위 테스트 - Rollback 발생 시 실패]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("STEP_METRICS 규칙 - ROLLBACK_ZERO 조건: RollbackCount가 3이면 실패")
    public void testEvaluateStepMetricsRule_ROLLBACK_Fail() {
        // [Given] RollbackCount가 3건 발생한 배치 로그
        String[] lines = {
            "StepName : FailStep",
            "ReadCount: 100",
            "WriteCount: 50",
            "CommitCount: 5",
            "RollbackCount: 3"
        };
        Rule rule = new Rule("STEP_METRICS", "", "ROLLBACK_ZERO", "롤백 검증");
        rule.stepName = "FailStep";

        // [When]
        RuleResult result = LogAnalyzer.evaluateRule("", lines, rule);

        // [Then]
        assertAll("Step 롤백 발생 실패 검증",
            () -> assertFalse(result.passed, "Rollback이 3이므로 실패해야 함"),
            () -> assertTrue(result.message.contains("3"), "실패 메시지에 롤백 수치(3) 포함")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [도메인 상태 전이 테스트] CheckResult 누적 상태 관리
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 하나의 JOB에 여러 규칙이 있을 때, 모든 규칙이 통과해야 `overallPassed == true`이며,
     *   단 하나의 규칙이라도 실패하면 `overallPassed == false`로 전이되는지 상태 머신을 테스트합니다.
     */
    @Test
    @DisplayName("CheckResult 상태 전이: 하나라도 실패 규칙이 추가되면 overallPassed가 false로 전이")
    public void testCheckResult_AddRuleResult() {
        // [Given] 신규 CheckResult 객체
        CheckResult cr = new CheckResult("01", "job1", "Job One");
        assertTrue(cr.overallPassed, "초기 상태는 true");

        // [When 1] 성공 규칙 추가
        RuleResult pass = new RuleResult("Test 1", "SEARCH", true, "OK");
        cr.addRuleResult(pass);
        // [Then 1]
        assertTrue(cr.overallPassed, "통과 규칙만 있을 때는 true 유지");

        // [When 2] 실패 규칙 추가
        RuleResult fail = new RuleResult("Test 2", "DISPLAY", false, "Failed");
        cr.addRuleResult(fail);
        // [Then 2]
        assertFalse(cr.overallPassed, "실패 규칙이 1개라도 추가되면 전체 상태가 false로 전이");
        assertEquals(2, cr.ruleResults.size(), "총 규칙 결과 수는 2개");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [비영업일 예외 처리 테스트]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 비영업일 실행 시 실패 상태였던 CheckResult가 비영업일 마킹(`markAsHoliday`)을 거치면
     *   정상(PASS, overallPassed = true)으로 복구 처리되는 비즈니스 로직을 검증합니다.
     */
    @Test
    @DisplayName("비영업일 예외 처리: markAsHoliday 호출 시 overallPassed가 정상(true)으로 복구")
    public void testCheckResult_MarkAsHoliday() {
        // [Given] 초기 실패 상태의 CheckResult
        CheckResult cr = new CheckResult("01", "job1", "Job One");
        cr.overallPassed = false;

        // [When] 비영업일 판정 마킹
        cr.markAsHoliday("Sunday");

        // [Then]
        assertAll("비영업일 상태 전이 단언",
            () -> assertTrue(cr.overallPassed, "비영업일 표시 후 정상(PASS)으로 전이"),
            () -> assertTrue(cr.isHoliday, "비영업일 플래그 true"),
            () -> assertEquals("Sunday", cr.holidayDetail, "비영업일 사유 문자열 저장")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [정규식(Regex) 기반 SEARCH 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("정규식 패턴 SEARCH: ERROR:\\s*\\d+ 패턴 2건 일치 검증")
    public void testEvaluateSearchRule_WithRegex() {
        // [Given]
        String logText = "ERROR: 001\nWARN: 002\nERROR: 003\n";
        String[] lines = logText.split("\n");
        Rule rule = new Rule("SEARCH", "", "EQUALS_N", "정규식 에러 카운트");
        rule.regex = "ERROR:\\s*\\d+";
        rule.expectedCount = 2;

        // [When]
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);

        // [Then]
        assertAll("정규식 매칭 검증",
            () -> assertTrue(result.passed, "ERROR 정규식 패턴 2개 일치"),
            () -> assertEquals("2건", result.extractedValue, "추출값 2건")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [콤마 포함 수치 파싱 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("콤마 포함 수치 파싱: '1,234,567' 숫자를 손실 없이 문자열로 정상 추출")
    public void testEvaluateDisplayRule_WithComma() {
        // [Given]
        String logText = "Total Processed: 1,234,567\nStatus: OK\n";
        String[] lines = logText.split("\n");
        Rule rule = new Rule("DISPLAY", "Total Processed", "COUNT_CHECK", "총 처리 건수");

        // [When]
        RuleResult result = LogAnalyzer.evaluateRule(logText, lines, rule);

        // [Then]
        assertAll("콤마 수치 추출 검증",
            () -> assertTrue(result.passed, "처리 성공"),
            () -> assertEquals("1,234,567", result.extractedValue, "콤마가 유지된 수치 추출")
        );
    }
}
