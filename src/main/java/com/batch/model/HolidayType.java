package com.batch.model;

/**
 * =====================================================================================
 * [비영업일 정책 유형 열거형 - HolidayType]
 * -------------------------------------------------------------------------------------
 * 💡 비영업일(휴일) 배치 처리 유형:
 * 1. LOG_CHECK: 배치는 실행되지만 비영업일 체크 후 안내 로그를 남김 (로그 감지 시 정상 판정)
 * 2. NO_RUN: 휴일에는 실행되지 않아 배치 로그 파일이 생성되지 않음 (파일 미생성 시 정상 판정)
 * 3. NONE: 휴일 여부와 무관하게 매일 정상 실행되어야 함
 * =====================================================================================
 */
public enum HolidayType {
    LOG_CHECK("LOG_CHECK", "비영업일 안내 로그 점검"),
    NO_RUN("NO_RUN", "비영업일 미실행 (파일 미생성 정상)"),
    NONE("NONE", "일반 실행 (휴일 무관)");

    private final String code;
    private final String description;

    HolidayType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isLogCheck() {
        return this == LOG_CHECK;
    }

    public boolean isNoRun() {
        return this == NO_RUN;
    }

    public static HolidayType fromString(String val) {
        if (val == null || val.trim().isEmpty()) {
            return NONE;
        }
        for (HolidayType type : values()) {
            if (type.code.equalsIgnoreCase(val.trim())) {
                return type;
            }
        }
        return NONE;
    }
}
