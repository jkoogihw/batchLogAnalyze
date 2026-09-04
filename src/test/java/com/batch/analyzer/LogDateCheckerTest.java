package com.batch.analyzer;

import com.batch.model.JobPolicy;
import com.batch.model.RuleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [단위 테스트 (Unit Test): LogDateChecker 배치 로그 일자 검증기]
 * -------------------------------------------------------------------------------------
 * 💡 검증 대상:
 * 1. 로그 텍스트 내 첫 번째 타임스탬프 파싱 정확성
 * 2. 6자리/8자리 작업 폴더명으로부터 BaseDate(분석 기준일) 해석
 * 3. 일간 배치 09:05 분기 기준 시각 기반 당일/전일 기대 일자 계산 및 판정
 * 4. 월간 배치(매월 2일 로그 생성) 기대 일자 계산 및 판정
 * 5. skipDateCheck 옵션 활성화 시 일자 무관 정상(PASS) 처리
 * =====================================================================================
 */
@DisplayName("단위 테스트: LogDateChecker 실행주기/시간 기반 로그 일자 정상 취합 점검 엔진")
public class LogDateCheckerTest {

    private final LogDateChecker checker = new LogDateChecker();

    @Test
    @DisplayName("로그 텍스트로부터 첫 번째 타임스탬프 정상 추출 검증")
    public void testExtractLogDateTime() {
        String logText = "INFO header\n" +
                "2026-08-22 03:05:02.131 INFO [Main.java:10] Starting batch job\n" +
                "2026-08-22 03:05:10.000 INFO [Main.java:20] Next line\n";

        LocalDateTime dt = LogDateChecker.extractLogDateTime(logText);
        assertNotNull(dt);
        assertEquals(2026, dt.getYear());
        assertEquals(8, dt.getMonthValue());
        assertEquals(22, dt.getDayOfMonth());
        assertEquals(3, dt.getHour());
        assertEquals(5, dt.getMinute());
        assertEquals(2, dt.getSecond());
    }

    @Test
    @DisplayName("작업 폴더명(6자리/8자리 날짜)으로부터 기준일(BaseDate) 파싱 검증")
    public void testResolveBaseDate() {
        LocalDate d1 = LogDateChecker.resolveBaseDate("260822", null);
        assertEquals(LocalDate.of(2026, 8, 22), d1);

        LocalDate d2 = LogDateChecker.resolveBaseDate("20260822", null);
        assertEquals(LocalDate.of(2026, 8, 22), d2);

        LocalDate fallback = LocalDate.of(2026, 9, 1);
        LocalDate d3 = LogDateChecker.resolveBaseDate("log_samples", fallback);
        assertEquals(fallback, d3);
    }

    @Test
    @DisplayName("일간 배치 (09:05 이전 실행): 당일 일자 로그 정상 취합(PASS) 검증")
    public void testDailyCurrentDay_Success() {
        // [Given] 03:05 실행 정책, 2026-08-22 일자의 로그
        JobPolicy policy = JobPolicy.daily("01", "gagastJob002", "03:05");
        policy.jobTitle = "추천터치고객";

        String logText = "2026-08-22 03:05:02.131 INFO Starting batch\n";

        // [When] 260822 폴더 기준 분석
        RuleResult result = checker.checkDate(logText, policy, "260822", false);

        // [Then]
        assertAll("03:05 당일 배치 PASS 검증",
            () -> assertEquals("01", result.ruleNo),
            () -> assertEquals("DATE_CHECK", result.type),
            () -> assertEquals("배치파일점검", result.description),
            () -> assertTrue(result.passed),
            () -> assertEquals("정상파일수집", result.message)
        );
    }

    @Test
    @DisplayName("일간 배치 (09:05 이전 실행): 일자 불일치 로그 감지 시 실패(FAIL) 처리")
    public void testDailyCurrentDay_FailOnOldDate() {
        // [Given] 03:05 실행 정책인데 과거 일자(2026-08-20)의 로그가 취합된 경우
        JobPolicy policy = JobPolicy.daily("01", "gagastJob002", "03:05");

        String logText = "2026-08-20 03:05:02.131 INFO Starting batch\n";

        // [When] 260822(2026-08-22) 폴더 기준 분석
        RuleResult result = checker.checkDate(logText, policy, "260822", false);

        // [Then]
        assertAll("일자 불일치 FAIL 검증",
            () -> assertFalse(result.passed),
            () -> assertTrue(result.message.contains("로그 일자 불일치")),
            () -> assertTrue(result.message.contains("2026-08-22")),
            () -> assertTrue(result.message.contains("2026-08-20"))
        );
    }

    @Test
    @DisplayName("일간 배치 (09:05 이후 실행): 전일자 로그 정상 취합(PASS) 검증")
    public void testDailyPreviousDay_Success() {
        // [Given] 11:00 실행 정책, 전일자(2026-08-21) 로그
        JobPolicy policy = JobPolicy.daily("04", "smrmJob102", "11:00");

        String logText = "2026-08-21 11:00:02.638 INFO Starting batch\n";

        // [When] 260822(2026-08-22) 기준 분석
        RuleResult result = checker.checkDate(logText, policy, "260822", false);

        // [Then]
        assertAll("11:00 전일 배치 PASS 검증",
            () -> assertTrue(result.passed),
            () -> assertEquals("정상파일수집", result.message)
        );
    }

    @Test
    @DisplayName("월간 배치 (MONTHLY): 매월 2일 생성 로그 정상 취합(PASS) 검증")
    public void testMonthlyBatch_Success() {
        // [Given] 월간 배치 정책 (매월 2일 로그 생성)
        JobPolicy policy = JobPolicy.monthly("90", "monthlyJob", 2, "01:00");

        String logText = "2026-08-02 01:00:00.000 INFO Monthly batch started\n";

        // [When] 260822(2026년 8월) 기준 분석
        RuleResult result = checker.checkDate(logText, policy, "260822", false);

        // [Then]
        assertAll("월간 배치 PASS 검증",
            () -> assertTrue(result.passed),
            () -> assertEquals("정상파일수집", result.message)
        );
    }

    @Test
    @DisplayName("skipDateCheck 옵션 활성화 시 시간/일자 무관 정상(PASS) 처리 검증")
    public void testSkipDateCheckOption() {
        // [Given] 과거 일자의 로그이지만 skipDateCheck=true 인 경우
        JobPolicy policy = JobPolicy.daily("01", "job1", "03:05");

        String logText = "2020-01-01 03:05:00.000 INFO Old log\n";

        // [When] skipDateCheck = true
        RuleResult result = checker.checkDate(logText, policy, "260822", true);

        // [Then]
        assertAll("일자 점검 스킵 검증",
            () -> assertTrue(result.passed),
            () -> assertTrue(result.message.contains("정상파일수집"))
        );
    }
}
