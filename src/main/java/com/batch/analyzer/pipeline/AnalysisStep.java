package com.batch.analyzer.pipeline;

import com.batch.model.CheckResult;

/**
 * =====================================================================================
 * [검증 단계 인터페이스 (Step Interface): AnalysisStep]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - 파이프라인 내 각 검증 단계(파일탐색, 일자검증, 비영업일검증, 룰평가 등)가 구현해야 하는 표준 인터페이스입니다.
 * =====================================================================================
 */
@FunctionalInterface
public interface AnalysisStep {

    /**
     * 특정 검증 단계를 수행합니다.
     *
     * @param context 파이프라인 실행 컨텍스트 (상태 공유용)
     * @param result  누적 검증 결과 객체
     * @return 파이프라인 흐름 제어 결과 (CONTINUE 또는 TERMINATE)
     * @throws Exception 실행 중 발생한 예외
     */
    StepResult execute(StepExecutionContext context, CheckResult result) throws Exception;
}
