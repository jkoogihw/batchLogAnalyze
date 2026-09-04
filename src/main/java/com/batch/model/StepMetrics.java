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

    public StepMetrics(String stepName, long readCount, long writeCount, long commitCount, long rollbackCount) {
        this.stepName = stepName;
        this.readCount = readCount;
        this.writeCount = writeCount;
        this.commitCount = commitCount;
        this.rollbackCount = rollbackCount;
    }

    public static StepMetrics of(String stepName, long readCount, long writeCount, long commitCount, long rollbackCount) {
        return new StepMetrics(stepName, readCount, writeCount, commitCount, rollbackCount);
    }

    public static Builder builder(String stepName) {
        return new Builder(stepName);
    }

    public static class Builder {
        private final String stepName;
        private long readCount = 0;
        private long writeCount = 0;
        private long commitCount = 0;
        private long rollbackCount = 0;

        public Builder(String stepName) {
            this.stepName = stepName;
        }

        public Builder readCount(long readCount) {
            this.readCount = readCount;
            return this;
        }

        public Builder writeCount(long writeCount) {
            this.writeCount = writeCount;
            return this;
        }

        public Builder commitCount(long commitCount) {
            this.commitCount = commitCount;
            return this;
        }

        public Builder rollbackCount(long rollbackCount) {
            this.rollbackCount = rollbackCount;
            return this;
        }

        public StepMetrics build() {
            return new StepMetrics(stepName, readCount, writeCount, commitCount, rollbackCount);
        }
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
