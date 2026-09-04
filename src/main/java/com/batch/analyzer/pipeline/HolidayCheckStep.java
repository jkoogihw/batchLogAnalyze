package com.batch.analyzer.pipeline;

import com.batch.analyzer.HolidayChecker;
import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.LogContent;

/**
 * =====================================================================================
 * [파이프라인 3단계: 비영업일 예외 검사 (HolidayCheckStep)]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - HolidayChecker를 통해 로그 내 비영업일 메시지를 확인하고,
 *   감지 시 정상 통과 마킹 후 후속 룰 평가를 건너뛰도록 조기 종료(TERMINATE)합니다.
 * =====================================================================================
 */
public class HolidayCheckStep implements AnalysisStep {

    private final HolidayChecker holidayChecker;

    public HolidayCheckStep(HolidayChecker holidayChecker) {
        this.holidayChecker = holidayChecker != null ? holidayChecker : new HolidayChecker();
    }

    @Override
    public StepResult execute(StepExecutionContext context, CheckResult result) throws Exception {
        LogContent logContent = context.getLogContent();
        JobPolicy policy = context.getPolicy();

        if (logContent == null || policy == null) {
            return StepResult.next();
        }

        boolean isHoliday = holidayChecker.checkAndApply(logContent.getFullText(), policy, result);
        if (isHoliday) {
            return StepResult.terminate("비영업일 예외 적용 -> 후속 룰 검증 건너뜀 (정상 처리)");
        }

        return StepResult.next();
    }
}
