package com.batch.model;

/**
 * =====================================================================================
 * [개별 규칙 검증 결과 모델 - RuleResult]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 개선 포인트:
 * 1. Enum 연동:
 *    - RuleType, ConditionType 지원으로 규칙 평가 결과의 타입 안전성 확보.
 * 2. 도메인 질의 메서드:
 *    - isPassed(), isFailed(), getRuleType(), getConditionType() 제공.
 * =====================================================================================
 */
public class RuleResult {
    
    public String ruleNo;           // 규칙 번호 (예: "01", "02")
    public String description;      // 규칙 설명
    public String type;             // 규칙 타입 (SEARCH, DISPLAY, STEP_METRICS, HOLIDAY, DATE_CHECK)
    public String target;           // 검색 대상
    public String extractedValue;   // 추출된 값
    public String condition;        // 검증 조건
    public boolean passed;          // 통과 여부
    public String message;          // 상세 메시지

    public RuleResult() {
    }

    public RuleResult(String description, String type, boolean passed, String message) {
        this("", description, type, passed, message);
    }

    public RuleResult(String ruleNo, String description, String type, boolean passed, String message) {
        this.ruleNo = ruleNo;
        this.description = description;
        this.type = type;
        this.passed = passed;
        this.message = message;
    }

    public RuleType getRuleType() {
        return RuleType.fromString(this.type);
    }

    public ConditionType getConditionType() {
        return ConditionType.fromString(this.condition);
    }

    public boolean isPassed() {
        return this.passed;
    }

    public boolean isFailed() {
        return !this.passed;
    }

    @Override
    public String toString() {
        return "RuleResult{" +
                "ruleNo='" + ruleNo + '\'' +
                ", description='" + description + '\'' +
                ", passed=" + passed +
                ", extractedValue='" + extractedValue + '\'' +
                '}';
    }
}
