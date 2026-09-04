package com.batch.config;

import com.batch.model.LogConstants;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * =====================================================================================
 * [타입 안전한 설정 접근자 (Type-Safe Configuration): BatchConfig]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 및 클린 코드 리팩토링 포인트:
 * 1. 문자열 키 하드코딩 제거 (Primitive/String Obsession 방지):
 *    - 프로젝트 전역에서 'Config.get("base.folder")' 처럼 문자열을 직접 쓰지 않고,
 *      'BatchConfig.getBaseFolder()'와 같이 타입 안전한 정적 메서드로 접근합니다.
 * 2. 타입 변환 및 기본값 중앙 관리:
 *    - boolean, Charset, int 등의 형변환과 fallback 기본값을 안전하게 처리합니다.
 * =====================================================================================
 */
public class BatchConfig {

    public static final String KEY_BASE_FOLDER = "base.folder";
    public static final String KEY_POLICY_META_FILE = "policy.meta.file";
    public static final String KEY_REPORT_PREFIX = "report.prefix";
    public static final String KEY_LOG_CUTOFF_TIME = "log.cutoff.time";
    public static final String KEY_LOG_DATE_CHECK_ENABLED = "log.date.check.enabled";
    public static final String KEY_FILE_ENCODING = "file.encoding";

    public static String getBaseFolder() {
        return Config.get(KEY_BASE_FOLDER, ".");
    }

    public static String getPolicyMetaFile() {
        return Config.get(KEY_POLICY_META_FILE, "policy_meta.json");
    }

    public static String getReportPrefix() {
        return Config.get(KEY_REPORT_PREFIX, "로그분석결과_");
    }

    public static String getLogCutoffTime() {
        return Config.get(KEY_LOG_CUTOFF_TIME, LogConstants.DEFAULT_CUTOFF_TIME);
    }

    public static boolean isDateCheckEnabled() {
        String val = Config.get(KEY_LOG_DATE_CHECK_ENABLED, "true");
        return "true".equalsIgnoreCase(val) || "1".equals(val) || "yes".equalsIgnoreCase(val);
    }

    public static Charset getFileEncoding() {
        String encodingName = Config.get(KEY_FILE_ENCODING, LogConstants.DEFAULT_ENCODING);
        try {
            return Charset.forName(encodingName);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
