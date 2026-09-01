package com.batch.model;

/**
 * Spring Batch Step 요약 통계
 * 
 * 로그에서 추출된 Step 실행 통계 메트릭
 */
public class StepMetrics {
    
    public String stepName;           // Step 이름
    public long readCount = 0;        // 읽은 항목 수
    public long writeCount = 0;       // 쓴 항목 수
    public long commitCount = 0;      // 커밋 횟수
    public long rollbackCount = 0;    // 롤백 횟수

    public StepMetrics() {
    }

    public StepMetrics(String stepName) {
        this.stepName = stepName;
    }

    @Override
    public String toString() {
        return String.format("StepMetrics{%s: R:%d / W:%d / C:%d / RB:%d}", 
                stepName, readCount, writeCount, commitCount, rollbackCount);
    }

    public String toDisplayString() {
        return String.format("R:%d / W:%d / C:%d / Rollback:%d", 
                readCount, writeCount, commitCount, rollbackCount);
    }
}
