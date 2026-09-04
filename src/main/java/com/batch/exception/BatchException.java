package com.batch.exception;

/**
 * =====================================================================================
 * [배치 분석 시스템 기본 도메인 예외: BatchException]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - 배치 로그 분석 시스템 내에서 발생하는 모든 도메인 예외의 최상위 추상/비검사(Unchecked) 예외입니다.
 * =====================================================================================
 */
public class BatchException extends RuntimeException {

    public BatchException(String message) {
        super(message);
    }

    public BatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
