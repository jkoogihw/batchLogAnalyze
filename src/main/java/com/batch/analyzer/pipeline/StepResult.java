package com.batch.analyzer.pipeline;

/**
 * =====================================================================================
 * [파이프라인 단계 실행 결과 & 흐름 제어: StepResult]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - 파이프라인의 다음 단계로 진행할지(CONTINUE),
 *   혹은 조건 만족(비영업일, 파일미발견 등)으로 조기 종료(TERMINATE)할지 제어합니다.
 * =====================================================================================
 */
public class StepResult {

    public enum Flow {
        CONTINUE,
        TERMINATE
    }

    private final Flow flow;
    private final String reason;

    private StepResult(Flow flow, String reason) {
        this.flow = flow;
        this.reason = reason != null ? reason : "";
    }

    public static StepResult next() {
        return new StepResult(Flow.CONTINUE, "");
    }

    public static StepResult terminate(String reason) {
        return new StepResult(Flow.TERMINATE, reason);
    }

    public boolean shouldContinue() {
        return this.flow == Flow.CONTINUE;
    }

    public boolean isTerminated() {
        return this.flow == Flow.TERMINATE;
    }

    public String getReason() {
        return reason;
    }
}
