package com.batch.exception;

/**
 * =====================================================================================
 * [배치 정책 파일 및 메타데이터 예외: PolicyNotFoundException]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - 배치 정책 JSON 파일이 존재하지 않거나 파싱할 수 없을 때 발생합니다.
 * =====================================================================================
 */
public class PolicyNotFoundException extends BatchException {

    public PolicyNotFoundException(String message) {
        super(message);
    }

    public PolicyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
