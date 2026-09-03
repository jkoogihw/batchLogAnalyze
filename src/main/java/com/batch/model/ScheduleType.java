package com.batch.model;

/**
 * =====================================================================================
 * [배치 실행 주기 열거형 - ScheduleType]
 * -------------------------------------------------------------------------------------
 * 💡 설계 의도:
 * - 일간(DAILY) 및 월간(MONTHLY) 스케줄 유형의 규격화 및 한글 표시 레이블 제공.
 * =====================================================================================
 */
public enum ScheduleType {
    DAILY("DAILY", "일", "일간 배치"),
    MONTHLY("MONTHLY", "월", "월간 배치"),
    UNKNOWN("UNKNOWN", "-", "미정의 주기");

    private final String code;
    private final String label;
    private final String description;

    ScheduleType(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 문자열로부터 ScheduleType을 안전하게 탐색 (기본값: DAILY)
     */
    public static ScheduleType fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return DAILY; // 기본값은 일간 배치
        }
        String normalized = text.trim().toUpperCase();
        for (ScheduleType type : values()) {
            if (type.code.equals(normalized) || type.name().equals(normalized)) {
                return type;
            }
        }
        return DAILY;
    }

    public boolean isDaily() {
        return this == DAILY;
    }

    public boolean isMonthly() {
        return this == MONTHLY;
    }
}
