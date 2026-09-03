package com.batch.model;

/**
 * =====================================================================================
 * [검증 조건 열거형 - ConditionType]
 * -------------------------------------------------------------------------------------
 * 💡 설계 의도:
 * - "COUNT_CHECK", "EQUALS_0", "EQUALS_N" 등의 조건을 컴파일 타임 타입으로 규격화.
 * - JSON 파싱 시 대소문자 무시 및 null-safe 매핑 지원.
 * =====================================================================================
 */
public enum ConditionType {
    COUNT_CHECK("COUNT_CHECK", "단순 건수 확인"),
    EQUALS_0("EQUALS_0", "0건 일치 체크"),
    EQUALS_N("EQUALS_N", "N건 일치 체크"),
    ROLLBACK_ZERO("ROLLBACK_ZERO", "Rollback 0건 체크"),
    ERROR_IF_PRESENT("ERROR_IF_PRESENT", "오류 발생 여부 체크"),
    UNKNOWN("UNKNOWN", "미정의 조건");

    private final String code;
    private final String description;

    ConditionType(String code, String description) {
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
     * 문자열로부터 ConditionType을 안전하게 탐색 (대소문자 무시, null-safe)
     */
    public static ConditionType fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return UNKNOWN;
        }
        String normalized = text.trim().toUpperCase();
        for (ConditionType condition : values()) {
            if (condition.code.equals(normalized) || condition.name().equals(normalized)) {
                return condition;
            }
        }
        return UNKNOWN;
    }

    public boolean isCountCheck() {
        return this == COUNT_CHECK;
    }

    public boolean isEqualsZero() {
        return this == EQUALS_0;
    }

    public boolean isEqualsN() {
        return this == EQUALS_N;
    }

    public boolean isRollbackZero() {
        return this == ROLLBACK_ZERO;
    }

    public boolean isErrorIfPresent() {
        return this == ERROR_IF_PRESENT;
    }
}
