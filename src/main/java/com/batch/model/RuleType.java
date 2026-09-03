package com.batch.model;

/**
 * =====================================================================================
 * [규칙 유형 열거형 - RuleType]
 * -------------------------------------------------------------------------------------
 * 💡 설계 의도:
 * - 문자열("SEARCH", "DISPLAY" 등) 오타로 인한 런타임 오류 방지 및 컴파일 타임 검증.
 * - JSON 파싱 및 레거시 문자열과의 완벽한 양방향 호환(fromString, toString) 지원.
 * =====================================================================================
 */
public enum RuleType {
    SEARCH("SEARCH", "전문 검색 패턴 매칭"),
    DISPLAY("DISPLAY", "키워드 기반 수치 추출"),
    STEP_METRICS("STEP_METRICS", "Spring Batch Step 통계 점검"),
    DATE_CHECK("DATE_CHECK", "배치 실행 일자 점검"),
    HOLIDAY("HOLIDAY", "비영업일 예외 점검"),
    UNKNOWN("UNKNOWN", "미정의 규칙");

    private final String code;
    private final String description;

    RuleType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 문자열로부터 RuleType을 안전하게 탐색 (대소문자 무시, null-safe)
     */
    public static RuleType fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return UNKNOWN;
        }
        String normalized = text.trim().toUpperCase();
        for (RuleType type : values()) {
            if (type.code.equals(normalized) || type.name().equals(normalized)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public boolean isSearch() {
        return this == SEARCH;
    }

    public boolean isDisplay() {
        return this == DISPLAY;
    }

    public boolean isStepMetrics() {
        return this == STEP_METRICS;
    }

    public boolean isDateCheck() {
        return this == DATE_CHECK;
    }

    public boolean isHoliday() {
        return this == HOLIDAY;
    }
}
