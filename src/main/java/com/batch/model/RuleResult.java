package com.batch.model;

/**
 * 개별 규칙 검증 결과
 */
public class RuleResult {
    
    public String description;      // 규칙 설명
    public String type;             // 규칙 타입 (SEARCH, DISPLAY, STEP_METRICS, HOLIDAY)
    public String target;           // 검색 대상
    public String extractedValue;   // 추출된 값
    public String condition;        // 검증 조건
    public boolean passed;          // 통과 여부
    public String message;          // 상세 메시지

    public RuleResult() {
    }

    public RuleResult(String description, String type, boolean passed, String message) {
        this.description = description;
        this.type = type;
        this.passed = passed;
        this.message = message;
    }

    @Override
    public String toString() {
        return "RuleResult{" +
                "description='" + description + '\'' +
                ", passed=" + passed +
                ", extractedValue='" + extractedValue + '\'' +
                '}';
    }
}
