package com.batch.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [단위 테스트 (Unit Test) 학습 예제 - 도메인 모델 및 DTO 검증]
 * -------------------------------------------------------------------------------------
 * 💡 학습 포인트:
 * 1. 도메인 객체 및 엔티티(Entity) 단위 테스트:
 *    - 데이터 모델(`JobPolicy`, `Rule`, `RuleResult`, `CheckResult`, `StepMetrics`)의
 *      생성자 초기화 상태, 기본값 설정, 필드 캡슐화 및 상태 전이 로직을 검증합니다.
 * 2. 비즈니스 규칙을 가진 도메인 메서드 검증:
 *    - 단순 getter/setter가 아닌, `addRuleResult()`에 따른 상태 전이(`overallPassed`)나
 *      `markAsHoliday()`에 따른 예외 처리 상태 복구 같은 핵심 도메인 행위(Behavior)를 검증합니다.
 * 3. 디스플레이 및 포맷팅 문자열(`toDisplayString`, `toString`) 검증:
 *    - 사용자 화면이나 리포트에 노출되는 포맷이 기대 형식(`R:1000 / Rollback:0` 등)을 충족하는지 확인합니다.
 * =====================================================================================
 */
@DisplayName("단위 테스트: 배치 도메인 모델(JobPolicy, Rule, CheckResult, StepMetrics) 검증")
public class ModelTest {
    
    /**
     * ---------------------------------------------------------------------------------
     * [JobPolicy 생성자 및 초기 상태 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("JobPolicy 생성: 필수 식별값 매핑 및 초기 빈 규칙 리스트 생성 확인")
    public void testJobPolicy_Creation() {
        // [Given & When] JobPolicy 객체 생성
        JobPolicy policy = new JobPolicy("01", "testJob", "Test Job Title", "test_");

        // [Then]
        assertAll("JobPolicy 생성자 필드 매핑 단언",
            () -> assertEquals("01", policy.jobNo, "jobNo 확인"),
            () -> assertEquals("testJob", policy.jobName, "jobName 확인"),
            () -> assertEquals("Test Job Title", policy.jobTitle, "jobTitle 확인"),
            () -> assertEquals("test_", policy.filePrefix, "filePrefix 확인"),
            () -> assertEquals(0, policy.rules.size(), "초기 규칙 목록은 비어있어야 함")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [Rule 생성자 및 필드 매핑 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("Rule 생성: type, target, condition, description 필드 매핑 검증")
    public void testRule_Creation() {
        Rule rule = new Rule("SEARCH", "SUCCESS", "EQUALS_0", "Success count");

        assertAll("Rule 필드 검증",
            () -> assertEquals("SEARCH", rule.type, "type 확인"),
            () -> assertEquals("SUCCESS", rule.target, "target 확인"),
            () -> assertEquals("EQUALS_0", rule.condition, "condition 확인"),
            () -> assertEquals("Success count", rule.description, "description 확인")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [RuleResult 생성 및 결과 상태 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("RuleResult 생성: 개별 검증 결과 및 성공 플래그 매핑 확인")
    public void testRuleResult_Creation() {
        RuleResult result = new RuleResult("Test rule", "SEARCH", true, "All OK");

        assertAll("RuleResult 필드 검증",
            () -> assertEquals("Test rule", result.description, "description 확인"),
            () -> assertEquals("SEARCH", result.type, "type 확인"),
            () -> assertTrue(result.passed, "passed 확인"),
            () -> assertEquals("All OK", result.message, "message 확인")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [CheckResult 초기 기본 상태 검증]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 기본값 검증: CheckResult는 생성 시점에 `overallPassed = true`, `fileFound = false`로
     *   올바르게 초기화되어야 합니다.
     */
    @Test
    @DisplayName("CheckResult 초기화: 기본 overallPassed=true 및 fileFound=false 상태 확인")
    public void testCheckResult_Creation() {
        CheckResult result = new CheckResult("01", "job1", "Job One");

        assertAll("CheckResult 초기 상태 검증",
            () -> assertEquals("01", result.jobNo, "jobNo 확인"),
            () -> assertEquals("job1", result.jobName, "jobName 확인"),
            () -> assertEquals("Job One", result.jobTitle, "jobTitle 확인"),
            () -> assertTrue(result.overallPassed, "초기 통과 상태는 true"),
            () -> assertFalse(result.fileFound, "초기 파일 존재 여부는 false")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [CheckResult 규칙 추가에 따른 상태 전이 로직 검증]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 핵심 비즈니스 메서드 `addRuleResult`의 동작을 상태 머신 관점에서 검증합니다.
     */
    @Test
    @DisplayName("CheckResult 규칙 추가: 실패 규칙(passed=false) 추가 시 overallPassed 상태 전이")
    public void testCheckResult_AddRuleResult() {
        // [Given] 초기 객체
        CheckResult result = new CheckResult("01", "job1", "Job One");

        // [When 1] 통과 규칙 추가
        RuleResult rule1 = new RuleResult("Rule 1", "SEARCH", true, "OK");
        result.addRuleResult(rule1);

        // [Then 1]
        assertEquals(1, result.ruleResults.size(), "규칙 1개 추가됨");
        assertTrue(result.overallPassed, "통과 규칙만 추가됨: 통과 상태 유지");

        // [When 2] 실패 규칙 추가
        RuleResult rule2 = new RuleResult("Rule 2", "DISPLAY", false, "Failed");
        result.addRuleResult(rule2);

        // [Then 2]
        assertEquals(2, result.ruleResults.size(), "규칙 2개 추가됨");
        assertFalse(result.overallPassed, "실패 규칙이 추가되었으므로 전체 통과 상태는 false로 변경");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [StepMetrics 생성 및 포맷 문자열 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("StepMetrics 포맷: R/W/C/Rollback 수치 매핑 및 toDisplayString 포맷 검증")
    public void testStepMetrics_Display() {
        // [Given] 메트릭 객체 생성 및 수치 주입
        StepMetrics metrics = new StepMetrics("TestStep");
        metrics.readCount = 1000;
        metrics.writeCount = 950;
        metrics.commitCount = 19;
        metrics.rollbackCount = 0;

        // [When] 디스플레이 문자열 생성
        String display = metrics.toDisplayString();

        // [Then]
        assertAll("StepMetrics 수치 및 출력 포맷 단언",
            () -> assertEquals("TestStep", metrics.stepName, "Step 이름 확인"),
            () -> assertEquals(1000, metrics.readCount, "ReadCount 확인"),
            () -> assertEquals(950, metrics.writeCount, "WriteCount 확인"),
            () -> assertEquals(19, metrics.commitCount, "CommitCount 확인"),
            () -> assertEquals(0, metrics.rollbackCount, "RollbackCount 확인"),
            () -> assertTrue(display.contains("R:1000"), "출력에 R:1000 포함"),
            () -> assertTrue(display.contains("Rollback:0"), "출력에 Rollback:0 포함")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [CheckResult 비영업일 예외 복구 로직 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("CheckResult 비영업일 예외: markAsHoliday 호출 시 overallPassed=true 정상 복구")
    public void testCheckResult_MarkAsHoliday() {
        // [Given] 초기 실패 상태
        CheckResult result = new CheckResult("01", "job1", "Job One");
        result.overallPassed = false;
        result.fileFound = false;

        // [When] 비영업일 마킹
        result.markAsHoliday("Sunday");

        // [Then]
        assertAll("비영업일 복구 단언",
            () -> assertTrue(result.isHoliday, "비영업일 플래그 true"),
            () -> assertEquals("Sunday", result.holidayDetail, "비영업일 상세정보 일치"),
            () -> assertTrue(result.overallPassed, "비영업일 처리 후 전체 통과(true)로 복구")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [Rule toString() 문자열 표현 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("Rule toString: 규칙 타입과 설명이 문자열에 포함되는지 확인")
    public void testRule_ToString() {
        Rule rule = new Rule("DISPLAY", "Total Records", "EQUALS_N", "Count check");
        rule.expectedCount = 100;

        String str = rule.toString();

        assertAll("Rule toString 검증",
            () -> assertTrue(str.contains("DISPLAY"), "Type이 포함됨"),
            () -> assertTrue(str.contains("Count check"), "Description이 포함됨")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [JobPolicy toString() 문자열 표현 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("JobPolicy toString: Job 이름과 규칙 개수가 문자열에 포함되는지 확인")
    public void testJobPolicy_ToString() {
        JobPolicy policy = new JobPolicy("01", "job1", "Job One", "job_");
        policy.rules.add(new Rule("SEARCH", "SUCCESS", "COUNT_CHECK", "Rule 1"));
        policy.rules.add(new Rule("DISPLAY", "Count", "EQUALS_0", "Rule 2"));

        String str = policy.toString();

        assertAll("JobPolicy toString 검증",
            () -> assertTrue(str.contains("job1"), "Job 이름이 포함됨"),
            () -> assertTrue(str.contains("2"), "규칙 개수가 포함됨")
        );
    }
}
