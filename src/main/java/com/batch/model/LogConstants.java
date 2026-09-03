package com.batch.model;

/**
 * =====================================================================================
 * [시스템 공통 상수 - LogConstants]
 * -------------------------------------------------------------------------------------
 * 💡 설계 의도:
 * - 매직 넘버(Magic Number) 및 매직 스트링(Magic String)을 한곳에서 중앙 집중 관리.
 * - 기본 분기 시각, 월간 기본 일자, 규칙 접두사, 축약 마커 등을 표준화.
 * =====================================================================================
 */
public final class LogConstants {

    private LogConstants() {
        // 인스턴스화 방지
    }

    // 기본 시간 및 날짜 설정
    public static final String DEFAULT_CUTOFF_TIME = "09:05";
    public static final int DEFAULT_MONTHLY_LOG_DAY = 2;
    public static final String DEFAULT_ENCODING = "UTF-8";

    // 규칙 번호 및 기본 식별자
    public static final String DEFAULT_DATE_CHECK_RULE_NO = "01";
    public static final String DATE_CHECK_DESCRIPTION = "배치파일점검";
    public static final String MSG_NORMAL_FILE_COLLECTED = "정상파일수집";
    public static final String MSG_FILE_NOT_FOUND = "해당 JOB의 로그 파일이 존재하지 않습니다.";
    public static final String RULE_NO_ERROR = "ERR";

    // 축약 마커 포맷
    public static final String TRIM_MARKER_PREFIX = "... [TRIMMED ";
    public static final String TRIM_MARKER_SUFFIX = " LINES] ...";
}
