package com.batch.exception;

/**
 * =====================================================================================
 * [로그 파일 I/O 및 파싱 예외: LogFileException]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - 대상 로그 파일 읽기 오류, 인코딩 불일치, 파일 권한 문제 발생 시 사용됩니다.
 * =====================================================================================
 */
public class LogFileException extends BatchException {

    public LogFileException(String message) {
        super(message);
    }

    public LogFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
