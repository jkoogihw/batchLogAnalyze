package com.batch.analyzer.pipeline;

import com.batch.analyzer.HolidayChecker;
import com.batch.analyzer.JobAnalysisContext;
import com.batch.model.CheckResult;
import com.batch.model.CheckStatus;
import com.batch.model.JobPolicy;
import com.batch.model.LogContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("단위 테스트: HolidayCheckStep 비영업일 판정 및 파이프라인 조기 종료 격리 검증")
class HolidayCheckStepTest {

    private final HolidayCheckStep step = new HolidayCheckStep(new HolidayChecker());

    @Test
    @DisplayName("비영업일 안내 문구 감지 시: CheckResult를 정상(HOLIDAY)으로 마킹하고 파이프라인 조기 종료(TERMINATE) 반환")
    void testExecute_HolidayMatched_TerminatesEarly() throws Exception {
        // [Given] 디스크 I/O 없이 순수 메모리 상의 LogContent 객체 생성
        LogContent log = LogContent.of("2026-08-22 03:00:00 [INFO] 비영업일에는 해당 JOB이 수행되지 않습니다.\n종료");
        JobPolicy policy = JobPolicy.builder("01", "JOB01")
                .holidayPattern("비영업일.*수행되지 않습니다")
                .build();

        JobAnalysisContext jobContext = JobAnalysisContext.builder().policy(policy).build();
        StepExecutionContext execContext = new StepExecutionContext(jobContext);
        execContext.setLogContent(log);

        CheckResult result = new CheckResult(policy);
        result.attachLogFile(new File("holiday.log"));

        // [When] HolidayCheckStep 단독 실행
        StepResult stepResult = step.execute(execContext, result);

        // [Then] 파이프라인 흐름 제어 및 도메인 상태 검증
        assertAll("비영업일 단계 실행 결과 단언",
                () -> assertTrue(stepResult.isTerminated(), "비영업일 감지 시 후속 파이프라인은 조기 종료되어야 함"),
                () -> assertTrue(result.isHoliday(), "비영업일 플래그 true"),
                () -> assertTrue(result.isPassed(), "비영업일은 정상(PASS)으로 판정"),
                () -> assertEquals(CheckStatus.HOLIDAY, result.getStatus(), "도메인 상태는 HOLIDAY"),
                () -> assertEquals(1, result.ruleResults.size(), "비영업일 확인 룰 결과 1건 등록")
        );
    }

    @Test
    @DisplayName("영업일 정상 로그일 시: 조기 종료 없이 다음 단계 진행(CONTINUE) 반환")
    void testExecute_NormalLog_Continues() throws Exception {
        // [Given] 일반 정상 로그
        LogContent log = LogContent.of("2026-08-22 03:00:00 [INFO] 정상 배치 가동\n배치 완료");
        JobPolicy policy = JobPolicy.builder("01", "JOB01")
                .holidayPattern("비영업일.*수행되지 않습니다")
                .build();

        JobAnalysisContext jobContext = JobAnalysisContext.builder().policy(policy).build();
        StepExecutionContext execContext = new StepExecutionContext(jobContext);
        execContext.setLogContent(log);

        CheckResult result = new CheckResult(policy);

        // [When]
        StepResult stepResult = step.execute(execContext, result);

        // [Then]
        assertAll("영업일 진행 상태 단언",
                () -> assertTrue(stepResult.shouldContinue(), "영업일일 경우 계속(CONTINUE) 진행되어야 함"),
                () -> assertFalse(result.isHoliday(), "비영업일 아님"),
                () -> assertEquals(0, result.ruleResults.size(), "규칙 결과 추가 없음")
        );
    }
}
