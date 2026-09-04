package com.batch.analyzer.pipeline;

import com.batch.analyzer.JobAnalysisContext;
import com.batch.analyzer.LogDateChecker;
import com.batch.model.CheckResult;
import com.batch.model.LogContent;
import com.batch.model.RuleResult;

/**
 * =====================================================================================
 * [파이프라인 2단계: 로그 일자 검증 (DateCheckStep)]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - LogDateChecker를 통해 로그 파일의 실제 생성/실행 일시와 배치 기대 일자의 일치 여부를 검증합니다.
 * =====================================================================================
 */
public class DateCheckStep implements AnalysisStep {

    private final LogDateChecker dateChecker;

    public DateCheckStep(LogDateChecker dateChecker) {
        this.dateChecker = dateChecker != null ? dateChecker : new LogDateChecker();
    }

    @Override
    public StepResult execute(StepExecutionContext context, CheckResult result) throws Exception {
        LogContent logContent = context.getLogContent();
        if (logContent == null) {
            return StepResult.next();
        }

        JobAnalysisContext jobCtx = context.getJobContext();
        RuleResult dateResult = dateChecker.checkDate(
                logContent.getFullText(),
                jobCtx.getPolicy(),
                jobCtx.getFolderName(),
                jobCtx.getLogFiles(),
                jobCtx.isSkipDateCheck()
        );

        result.addRuleResult(dateResult);
        return StepResult.next();
    }
}
