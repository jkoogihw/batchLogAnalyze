package com.batch.report;

import com.batch.model.CheckResult;

import java.io.File;
import java.util.List;

/**
 * =====================================================================================
 * [인터페이스 분리 원칙 (ISP): 리포트 작성 인터페이스]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 설계 원칙:
 * 1. ISP (Interface Segregation Principle):
 *    - 리포트 출력/작성에 필요한 표준 계약을 정의하여 콘솔, 마크다운, 향후 HTML/PDF 등의
 *      다양한 리포트 생성기로 쉽게 확장할 수 있도록 합니다.
 * =====================================================================================
 */
public interface ReportWriter {

    /**
     * 리포트 작성 및 출력
     *
     * @param folderName 대상 폴더명
     * @param results    분석 결과 목록
     * @param total      전체 건수
     * @param pass       정상 건수
     * @param fail       실패 건수
     * @return 생성된 리포트 파일 객체 (콘솔 출력의 경우 null 가능)
     */
    File write(String folderName, List<CheckResult> results, int total, int pass, int fail);
}
