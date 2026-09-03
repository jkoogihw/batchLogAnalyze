package com.batch.model;

/**
 * =====================================================================================
 * [검증 상태 열거형 - CheckStatus]
 * -------------------------------------------------------------------------------------
 * 💡 설계 의도:
 * - PASS, FAIL, HOLIDAY, SKIP, FILE_NOT_FOUND 등의 상태값을 표준화.
 * - 콘솔 및 마크다운 리포트에 사용되는 이모지/라벨을 캡슐화.
 * =====================================================================================
 */
public enum CheckStatus {
    PASS("PASS", "✅ 정상", "OK"),
    FAIL("FAIL", "❌ 오류", "FAIL"),
    HOLIDAY("HOLIDAY", "🏖️ 비영업일", "HOLIDAY"),
    SKIP("SKIP", "⏭️ 점검제외", "SKIP"),
    FILE_NOT_FOUND("FILE_NOT_FOUND", "⚠️ 파일미발견", "NO_FILE"),
    ERROR("ERROR", "🔥 시스템에러", "ERROR");

    private final String code;
    private final String label;
    private final String shortBadge;

    CheckStatus(String code, String label, String shortBadge) {
        this.code = code;
        this.label = label;
        this.shortBadge = shortBadge;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getShortBadge() {
        return shortBadge;
    }

    public boolean isPassed() {
        return this == PASS || this == HOLIDAY || this == SKIP;
    }

    public boolean isFailed() {
        return !isPassed();
    }
}
