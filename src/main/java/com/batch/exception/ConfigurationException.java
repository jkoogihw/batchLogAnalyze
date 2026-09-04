package com.batch.exception;

/**
 * =====================================================================================
 * [환경설정 오류 예외: ConfigurationException]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - application.properties 파일 누락, 필수 키 누락, 부적절한 설정값 파싱 오류 시 발생합니다.
 * =====================================================================================
 */
public class ConfigurationException extends BatchException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
