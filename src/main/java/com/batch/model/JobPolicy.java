package com.batch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 배치 JOB 검증 정책 모델
 * 
 * JSON 정책 파일에서 각 JOB에 대한 검증 규칙을 정의합니다.
 */
public class JobPolicy {
    
    public String jobNo;              // JOB 번호 (예: "01")
    public String jobName;            // JOB 명 (예: "smrmJob001")
    public String jobTitle;           // JOB 설명 (예: "SM RM Job001")
    public String filePrefix;         // 로그 파일 접두사 (예: "smrm_")
    public String rawPattern;         // 원본 파일명 패턴 (미변경 명명규칙)
    public String holidayPattern;     // 비영업일 감지 패턴
    public List<Rule> rules = new ArrayList<>();  // 검증 규칙 목록

    public JobPolicy() {
    }

    public JobPolicy(String jobNo, String jobName, String jobTitle, String filePrefix) {
        this.jobNo = jobNo;
        this.jobName = jobName;
        this.jobTitle = jobTitle;
        this.filePrefix = filePrefix;
    }

    @Override
    public String toString() {
        return "JobPolicy{" +
                "jobNo='" + jobNo + '\'' +
                ", jobName='" + jobName + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", filePrefix='" + filePrefix + '\'' +
                ", rulesCount=" + rules.size() +
                '}';
    }
}
