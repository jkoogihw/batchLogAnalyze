package com.batch.analyzer.pipeline;

import com.batch.analyzer.HolidayChecker;
import com.batch.analyzer.JobAnalysisContext;
import com.batch.analyzer.LogDateChecker;
import com.batch.analyzer.LogFileLocator;
import com.batch.analyzer.evaluator.RuleEvaluatorRegistry;
import com.batch.model.CheckResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * =====================================================================================
 * [분석 파이프라인 엔진 (Pipeline Engine): AnalysisPipeline]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 설계 의도 & 리팩토링 포인트:
 * 1. 책임 연쇄 패턴 (Chain of Responsibility) 적용:
 *    - 파일 탐색, 일자 검증, 비영업일 판정, 룰 평가 등 각 검증 단계를 독립된 Step으로 분리하고
 *      파이프라인 형태로 순차 실행합니다.
 * 2. 개방-폐쇄 원칙 (OCP) 극대화:
 *    - 새로운 검증 단계(예: 파일 무결성 해시, 용량 검사 등)가 추가되더라도
 *      기존 코드를 수정하지 않고 새로운 Step을 파이프라인에 주입하는 것만으로 손쉽게 확장 가능합니다.
 * 3. 중앙 집중식 예외 처리 및 도메인 상태 캡슐화:
 *    - 파이프라인 실행 중 발생하는 임의의 I/O 또는 파싱 오류를 포착하여
 *      CheckResult의 도메인 메서드(recordAnalysisFailure)를 통해 안전하게 오류 상태를 반영합니다.
 * =====================================================================================
 */
public class AnalysisPipeline {

    private final List<AnalysisStep> steps;

    public AnalysisPipeline(List<AnalysisStep> steps) {
        this.steps = steps != null ? Collections.unmodifiableList(new ArrayList<>(steps)) : Collections.emptyList();
    }

    /**
     * 표준 배치 로그 분석 파이프라인 구성 팩토리
     */
    public static AnalysisPipeline standard(LogFileLocator fileLocator,
                                            HolidayChecker holidayChecker,
                                            LogDateChecker dateChecker,
                                            RuleEvaluatorRegistry registry) {
        List<AnalysisStep> steps = new ArrayList<>();
        steps.add(new FileLocateStep(fileLocator));
        steps.add(new HolidayCheckStep(holidayChecker));
        steps.add(new DateCheckStep(dateChecker));
        steps.add(new RuleEvaluationStep(registry));
        return new AnalysisPipeline(steps);
    }

    public static AnalysisPipeline standard() {
        return standard(new LogFileLocator(), new HolidayChecker(), new LogDateChecker(), new RuleEvaluatorRegistry());
    }

    /**
     * 파이프라인 실행
     */
    public CheckResult execute(JobAnalysisContext jobContext) {
        if (jobContext == null) {
            CheckResult nullResult = new CheckResult();
            nullResult.recordAnalysisFailure("파라미터 오류", "JobAnalysisContext가 null입니다.");
            return nullResult;
        }

        CheckResult result = new CheckResult(jobContext.getPolicy());
        StepExecutionContext execContext = new StepExecutionContext(jobContext);

        try {
            for (AnalysisStep step : steps) {
                StepResult stepResult = step.execute(execContext, result);
                if (stepResult != null && stepResult.isTerminated()) {
                    break;
                }
            }
        } catch (Throwable t) {
            result.recordAnalysisFailure(t);
        }

        return result;
    }

    public List<AnalysisStep> getSteps() {
        return steps;
    }
}
