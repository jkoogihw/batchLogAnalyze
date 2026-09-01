package com.batch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * JOB별 검증 결과
 */
public class CheckResult {
    
    public String jobNo;                        // JOB 번호
    public String jobName;                      // JOB 명
    public String jobTitle;                     // JOB 설명
    public String fileName;                     // 검증한 로그 파일명
    public boolean fileFound = false;           // 로그 파일 존재 여부
    public boolean isHoliday = false;           // 비영업일 여부
    public String holidayDetail = "";           // 비영업일 상세 정보
    public List<RuleResult> ruleResults = new ArrayList<>();  // 규칙별 검증 결과
    public boolean overallPassed = true;        // 전체 통과 여부

    public CheckResult() {
    }

    public CheckResult(String jobNo, String jobName, String jobTitle) {
        this.jobNo = jobNo;
        this.jobName = jobName;
        this.jobTitle = jobTitle;
    }

    /**
     * 검증 결과를 추가합니다
     */
    public void addRuleResult(RuleResult ruleResult) {
        ruleResults.add(ruleResult);
        if (!ruleResult.passed) {
            overallPassed = false;
        }
    }

    /**
     * 비영업일 처리 - 모든 규칙을 통과 처리
     */
    public void markAsHoliday(String detail) {
        this.isHoliday = true;
        this.holidayDetail = detail;
        this.overallPassed = true;
    }

    @Override
    public String toString() {
        return "CheckResult{" +
                "jobNo='" + jobNo + '\'' +
                ", jobName='" + jobName + '\'' +
                ", fileFound=" + fileFound +
                ", overallPassed=" + overallPassed +
                ", ruleResults=" + ruleResults.size() +
                '}';
    }
}
