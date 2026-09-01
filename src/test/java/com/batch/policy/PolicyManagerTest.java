package com.batch.policy;

import com.batch.model.JobPolicy;
import com.batch.model.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [단위 테스트 (Unit Test) 학습 예제 - JSON 직렬화/역직렬화 및 정책 매니저]
 * -------------------------------------------------------------------------------------
 * 💡 학습 포인트:
 * 1. 데이터 매핑 및 파싱 검증:
 *    - JSON 문자열이 도메인 모델(`JobPolicy`, `Rule`)로 변환될 때 각 필드(jobNo, jobName, filePrefix, rules)가
 *      타입 손실 없이 정확하게 매핑되는지 검증합니다.
 * 2. 복합 객체 그래프(Composite Object Graph) 검증:
 *    - JobPolicy 내부에 중첩된 `Rule` 리스트, `holidayCheck` 설정, 정규식 이스케이프 문자(`\\d+`) 등이
 *      계층 구조대로 온전하게 파싱되는지 확인합니다.
 * 3. 빈 데이터/경계값 처리:
 *    - 빈 배열 `[]` 입력 시 NPE 없이 빈 리스트를 반환하는지 확인합니다.
 * =====================================================================================
 */
@DisplayName("단위 테스트: PolicyManager JSON 정책 파일 파싱 및 도메인 객체 변환")
public class PolicyManagerTest {
    
    /**
     * ---------------------------------------------------------------------------------
     * [기본 단일 정책 파싱 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("단일 JOB 정책 파싱: 기본 메타 필드(jobNo, jobName, filePrefix, rules) 매핑 확인")
    public void testParseJsonPolicies_Basic() {
        // [Given] 1개의 JOB 정책을 담은 JSON 문자열
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"testJob001\", " +
                "\"jobTitle\": \"Test Job One\", \"filePrefix\": \"test_\", " +
                "\"rules\": [{\"type\": \"SEARCH\", \"target\": \"SUCCESS\", " +
                "\"condition\": \"COUNT_CHECK\", \"description\": \"Success count\"}]}]";

        // [When] JSON 파싱 실행
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);

        // [Then] 객체 필드 값 전수 단언
        assertNotNull(policies, "파싱 결과 리스트는 null이 아니어야 함");
        assertEquals(1, policies.size(), "정책 1개가 파싱되어야 함");

        JobPolicy job = policies.get(0);
        assertAll("JobPolicy 필드 매핑 단언",
            () -> assertEquals("01", job.jobNo, "Job 번호 일치"),
            () -> assertEquals("testJob001", job.jobName, "Job 이름 일치"),
            () -> assertEquals("test_", job.filePrefix, "파일 접두사 일치"),
            () -> assertEquals(1, job.rules.size(), "하위 규칙 1개 포함")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [다중 정책 배열 파싱 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("다중 JOB 정책 파싱: 2개 이상의 JOB 정책 배열 정상 변환")
    public void testParseJsonPolicies_Multiple() {
        // [Given] 2개의 JOB 메타를 가진 JSON
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", \"rules\": []}, " +
                "{\"jobNo\": \"02\", \"jobName\": \"job2\", " +
                "\"jobTitle\": \"Job Two\", \"filePrefix\": \"job2_\", \"rules\": []}]";

        // [When]
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);

        // [Then]
        assertEquals(2, policies.size(), "정책 2개가 파싱되어야 함");
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [SEARCH 규칙 세부 필드 파싱 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("SEARCH 규칙 파싱: type, target, condition 필드 매핑 검증")
    public void testParseJsonPolicies_SearchRule() {
        // [Given] SEARCH 룰을 포함한 JSON
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"SEARCH\", \"target\": \"ERROR\", " +
                "\"condition\": \"EQUALS_0\", \"description\": \"No errors\"}]}]";

        // [When]
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        Rule rule = policies.get(0).rules.get(0);

        // [Then]
        assertAll("SEARCH 룰 필드 검증",
            () -> assertEquals("SEARCH", rule.type, "규칙 타입 확인"),
            () -> assertEquals("ERROR", rule.target, "검색 대상 확인"),
            () -> assertEquals("EQUALS_0", rule.condition, "조건 확인")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [DISPLAY 규칙 세부 필드 파싱 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("DISPLAY 규칙 파싱: expectedCount 수치형 필드 매핑 검증")
    public void testParseJsonPolicies_DisplayRule() {
        // [Given] expectedCount: 100을 포함한 JSON
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"DISPLAY\", \"target\": \"Total Records\", " +
                "\"condition\": \"EQUALS_N\", \"expectedCount\": 100, " +
                "\"description\": \"Record count\"}]}]";

        // [When]
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        Rule rule = policies.get(0).rules.get(0);

        // [Then]
        assertAll("DISPLAY 룰 필드 검증",
            () -> assertEquals("DISPLAY", rule.type, "규칙 타입 확인"),
            () -> assertEquals(100, rule.expectedCount, "기대값(100) 수치 매핑 확인")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [STEP_METRICS 규칙 세부 필드 파싱 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("STEP_METRICS 규칙 파싱: stepName 필드 매핑 검증")
    public void testParseJsonPolicies_StepMetricsRule() {
        // [Given]
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"STEP_METRICS\", \"stepName\": \"ProcessStep\", " +
                "\"condition\": \"ROLLBACK_ZERO\", \"description\": \"Rollback count\"}]}]";

        // [When]
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        Rule rule = policies.get(0).rules.get(0);

        // [Then]
        assertAll("STEP_METRICS 룰 필드 검증",
            () -> assertEquals("STEP_METRICS", rule.type, "규칙 타입 확인"),
            () -> assertEquals("ProcessStep", rule.stepName, "Step 이름 확인")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [비영업일(holidayCheck) 중첩 객체 파싱 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("비영업일 설정 파싱: holidayCheck 중첩 객체 및 패턴 추출 검증")
    public void testParseJsonPolicies_HolidayPattern() {
        // [Given]
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"holidayCheck\": {\"pattern\": \"Saturday|Sunday\"}, " +
                "\"rules\": []}]";

        // [When]
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        JobPolicy job = policies.get(0);

        // [Then]
        assertAll("비영업일 패턴 검증",
            () -> assertNotNull(job.holidayPattern, "비영업일 패턴이 파싱되어야 함"),
            () -> assertTrue(job.holidayPattern.contains("Saturday"), "패턴에 Saturday 포함 확인")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [정규식 이스케이프 문자 파싱 검증]
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - JSON 내의 역슬래시(`\\`) 이스케이프가 자바 정규식 패턴으로 올바르게 보존되는지 확인합니다.
     */
    @Test
    @DisplayName("정규식 필드 파싱: 역슬래시 이스케이프 문자의 정상 보존 검증")
    public void testParseJsonPolicies_RegexField() {
        // [Given] 역슬래시가 포함된 정규식 JSON
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"SEARCH\", \"regex\": \"ERROR:\\\\\\\\s*\\\\\\\\d+\", " +
                "\"condition\": \"EQUALS_0\", \"description\": \"Error pattern\"}]}]";

        // [When]
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        Rule rule = policies.get(0).rules.get(0);

        // [Then]
        assertAll("정규식 이스케이프 검증",
            () -> assertNotNull(rule.regex, "Regex가 파싱되어야 함"),
            () -> assertTrue(rule.regex.contains("\\"), "역슬래시가 정상 보존되어야 함")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [다중 규칙 혼합 파싱 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("복합 룰 파싱: SEARCH와 DISPLAY 룰이 순서대로 리스트에 추가되는지 검증")
    public void testParseJsonPolicies_MultipleRules() {
        // [Given] 2개의 서로 다른 타입 규칙을 가진 JSON
        String json = "[{\"jobNo\": \"01\", \"jobName\": \"job1\", " +
                "\"jobTitle\": \"Job One\", \"filePrefix\": \"job1_\", " +
                "\"rules\": [{\"type\": \"SEARCH\", \"target\": \"SUCCESS\", " +
                "\"condition\": \"COUNT_CHECK\", \"description\": \"Rule 1\"}, " +
                "{\"type\": \"DISPLAY\", \"target\": \"Total\", " +
                "\"condition\": \"EQUALS_N\", \"expectedCount\": 100, " +
                "\"description\": \"Rule 2\"}]}]";

        // [When]
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        JobPolicy job = policies.get(0);

        // [Then]
        assertAll("다중 규칙 파싱 순서 및 타입 검증",
            () -> assertEquals(2, job.rules.size(), "규칙 2개가 파싱되어야 함"),
            () -> assertEquals("SEARCH", job.rules.get(0).type, "첫 번째 규칙 타입은 SEARCH"),
            () -> assertEquals("DISPLAY", job.rules.get(1).type, "두 번째 규칙 타입은 DISPLAY")
        );
    }
    
    /**
     * ---------------------------------------------------------------------------------
     * [빈 배열 엣지 케이스 파싱 검증]
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("엣지 케이스: 빈 JSON 배열('[]') 파싱 시 빈 리스트(size == 0) 반환")
    public void testParseJsonPolicies_Empty() {
        String json = "[]";
        List<JobPolicy> policies = PolicyManager.parseJsonPolicies(json);
        assertNotNull(policies, "결과 리스트는 null이 아니어야 함");
        assertEquals(0, policies.size(), "빈 배열 파싱 결과는 size 0");
    }
}
