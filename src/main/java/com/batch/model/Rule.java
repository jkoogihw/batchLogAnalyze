package com.batch.model;

/**
 * 개별 검증 규칙
 * 
 * 규칙 타입:
 * - SEARCH: 전체 텍스트에서 패턴 건수 집계
 * - DISPLAY: 키워드 뒤의 수치 추출
 * - STEP_METRICS: Spring Batch Step 통계 검증
 */
public class Rule {
    
    public String ruleNo;         // 규칙 번호 (예: "01", "02")
    public String type;           // DISPLAY, SEARCH, STEP_METRICS, DATE_CHECK
    public String target;         // 검색 대상 텍스트
    public String regex;          // 정규식 패턴
    public String stepName;       // Step 이름 (STEP_METRICS에서만 사용)
    public String condition;      // EQUALS_0, EQUALS_N, COUNT_CHECK, ROLLBACK_ZERO, ERROR_IF_PRESENT
    public int expectedCount = 0; // 기대값 (EQUALS_N에서만 사용)
    public String description;    // 규칙 설명

    public Rule() {
    }

    public Rule(String type, String target, String condition, String description) {
        this("", type, target, condition, description);
    }

    public Rule(String ruleNo, String type, String target, String condition, String description) {
        this.ruleNo = ruleNo;
        this.type = type;
        this.target = target;
        this.condition = condition;
        this.description = description;
    }

    @Override
    public String toString() {
        return "Rule{" +
                "ruleNo='" + ruleNo + '\'' +
                ", type='" + type + '\'' +
                ", description='" + description + '\'' +
                ", target='" + (target != null && target.length() > 20 ? target.substring(0, 20) + "..." : target) + '\'' +
                ", condition='" + condition + '\'' +
                '}';
    }
}
