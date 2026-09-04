package com.batch.model;

import com.batch.config.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * =====================================================================================
 * [JOB별 검증 결과 모델 - CheckResult]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 개선 포인트:
 * 1. 풍부한 도메인 메서드 캡슐화 ("Tell, Don't Ask" 원칙):
 *    - 외부 리포트/서비스가 내부 boolean 필드를 일일이 검사하지 않고,
 *      getStatus(), isPassed(), isFailed() 등의 메서드로 상태를 직접 질의합니다.
 * 2. 하드코딩 제거 및 설정 연동:
 *    - 스케줄 분기 시각(09:05) 및 월간 기본 일자(2일)를 LogConstants 및 Config로 중앙 관리합니다.
 * =====================================================================================
 */
public class CheckResult {
    
    public String jobNo;                        // JOB 번호
    public String jobName;                      // JOB 명
    public String jobTitle;                     // JOB 설명
    public String scheduleInfo = "";            // 배치 스케줄 정보 (예: "03:05 [당일 / 일]")
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

    public CheckResult(JobPolicy policy) {
        if (policy != null) {
            this.jobNo = policy.jobNo;
            this.jobName = policy.jobName;
            this.jobTitle = policy.jobTitle;
            this.scheduleInfo = formatScheduleInfo(policy);
        }
    }

    public static CheckResult of(JobPolicy policy) {
        return new CheckResult(policy);
    }

    public static Builder builder(String jobNo, String jobName) {
        return new Builder(jobNo, jobName);
    }

    public static class Builder {
        private final CheckResult result;

        public Builder(String jobNo, String jobName) {
            this.result = new CheckResult(jobNo, jobName, jobName);
        }

        public Builder jobTitle(String title) {
            this.result.jobTitle = title;
            return this;
        }

        public Builder scheduleInfo(String scheduleInfo) {
            this.result.scheduleInfo = scheduleInfo;
            return this;
        }

        public Builder fileName(String fileName) {
            this.result.fileName = fileName;
            return this;
        }

        public Builder fileFound(boolean fileFound) {
            this.result.fileFound = fileFound;
            return this;
        }

        public Builder overallPassed(boolean overallPassed) {
            this.result.overallPassed = overallPassed;
            return this;
        }

        public CheckResult build() {
            return this.result;
        }
    }

    /**
     * 배치 스케줄 정보 서식화 (예: "03:05 [당일 / 일]")
     */
    public static String formatScheduleInfo(JobPolicy policy) {
        if (policy == null) return "";
        String time = (policy.scheduleTime != null && !policy.scheduleTime.trim().isEmpty()) ? policy.scheduleTime.trim() : "-";
        ScheduleType scheduleType = policy.getScheduleType();
        boolean isMonthly = scheduleType.isMonthly();
        String cycle = scheduleType.getLabel();
        
        String targetDayDesc;
        if (isMonthly) {
            int day = policy.monthlyLogDay != null ? policy.monthlyLogDay : LogConstants.DEFAULT_MONTHLY_LOG_DAY;
            targetDayDesc = day + "일";
        } else {
            String cutoff = Config.get("log.cutoff.time", LogConstants.DEFAULT_CUTOFF_TIME);
            if (!time.equals("-") && time.compareTo(cutoff) > 0) {
                targetDayDesc = "전일";
            } else {
                targetDayDesc = "당일";
            }
        }
        return String.format("%s [%s / %s]", time, targetDayDesc, cycle);
    }

    /**
     * 검증 결과를 추가합니다
     */
    public void addRuleResult(RuleResult ruleResult) {
        if (ruleResult != null) {
            ruleResults.add(ruleResult);
            if (!ruleResult.passed) {
                overallPassed = false;
            }
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

    /**
     * 파일 미존재 상태로 마킹
     */
    public void markAsFileNotFound(String expectedFileName) {
        this.fileFound = false;
        this.fileName = expectedFileName != null ? expectedFileName : "파일 미발견";
        this.overallPassed = false;
    }

    // =========================================================================
    // 도메인 질의 메서드 (Tell, Don't Ask)
    // =========================================================================

    public boolean isPassed() {
        return this.overallPassed;
    }

    public boolean isFailed() {
        return !this.overallPassed;
    }

    public boolean isHoliday() {
        return this.isHoliday;
    }

    public boolean isFileFound() {
        return this.fileFound;
    }

    public CheckStatus getStatus() {
        if (!fileFound) {
            return CheckStatus.FILE_NOT_FOUND;
        }
        if (isHoliday) {
            return CheckStatus.HOLIDAY;
        }
        return overallPassed ? CheckStatus.PASS : CheckStatus.FAIL;
    }

    @Override
    public String toString() {
        return "CheckResult{" +
                "jobNo='" + jobNo + '\'' +
                ", jobName='" + jobName + '\'' +
                ", fileFound=" + fileFound +
                ", overallPassed=" + overallPassed +
                ", status=" + getStatus() +
                ", ruleResults=" + ruleResults.size() +
                '}';
    }
}
