package com.batch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * =====================================================================================
 * [배치 JOB 검증 정책 모델 - JobPolicy]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 개선 포인트:
 * 1. Enum 기반 주기 관리:
 *    - ScheduleType 연동으로 일간(DAILY)/월간(MONTHLY) 스케줄을 명확히 구분.
 * 2. Fluent Builder 패턴 제공:
 *    - JobPolicy.builder(...)를 통해 가독성 높은 테스트 픽스처 및 정책 생성 지원.
 * 3. 100% 하위 호환성 유지:
 *    - 기존 public 필드 및 생성자를 유지하여 기존 JSON 파서/테스트 코드 변경 불필요.
 * =====================================================================================
 */
public class JobPolicy {
    
    public String jobNo;              // JOB 번호 (예: "01")
    public String jobName;            // JOB 명 (예: "smrmJob001")
    public String jobTitle;           // JOB 설명 (예: "SM RM Job001")
    public String filePrefix;         // 로그 파일 접두사 (예: "smrm_")
    public String rawPattern;         // 원본 파일명 패턴 (미변경 명명규칙)
    public String holidayPattern;     // 비영업일 감지 패턴
    public String scheduleType;       // 실행 주기 ("DAILY", "MONTHLY", 기본값 "DAILY")
    public String scheduleTime;       // 실행 예정 시각 (예: "03:05", "11:00", "09:05")
    public Integer monthlyLogDay;     // 월간 배치 로그 생성 일자 (예: 2 -> 매월 2일 생성)
    public List<Rule> rules = new ArrayList<>();  // 검증 규칙 목록

    public JobPolicy() {
    }

    public JobPolicy(String jobNo, String jobName, String jobTitle, String filePrefix) {
        this.jobNo = jobNo;
        this.jobName = jobName;
        this.jobTitle = jobTitle;
        this.filePrefix = filePrefix;
        this.scheduleType = ScheduleType.DAILY.getCode();
    }

    public ScheduleType getScheduleType() {
        return ScheduleType.fromString(this.scheduleType);
    }

    public boolean isMonthly() {
        return getScheduleType().isMonthly();
    }

    public boolean isDaily() {
        return getScheduleType().isDaily();
    }

    // =========================================================================
    // Fluent Builder 및 팩토리 메서드 (테스트 픽스처 작성 및 정책 생성 생산성 극대화)
    // =========================================================================

    public static JobPolicy daily(String jobNo, String jobName, String scheduleTime) {
        return builder(jobNo, jobName).daily(scheduleTime).build();
    }

    public static JobPolicy monthly(String jobNo, String jobName, int monthlyLogDay, String scheduleTime) {
        return builder(jobNo, jobName).monthly(monthlyLogDay, scheduleTime).build();
    }

    public static Builder builder(String jobNo, String jobName) {
        return new Builder(jobNo, jobName);
    }

    public static class Builder {
        private final JobPolicy policy;

        public Builder(String jobNo, String jobName) {
            this.policy = new JobPolicy(jobNo, jobName, jobName, jobNo + "_" + jobName + "_");
        }

        public Builder title(String title) {
            this.policy.jobTitle = title;
            return this;
        }

        public Builder filePrefix(String prefix) {
            this.policy.filePrefix = prefix;
            return this;
        }

        public Builder rawPattern(String pattern) {
            this.policy.rawPattern = pattern;
            return this;
        }

        public Builder holidayPattern(String pattern) {
            this.policy.holidayPattern = pattern;
            return this;
        }

        public Builder daily(String scheduleTime) {
            this.policy.scheduleType = ScheduleType.DAILY.getCode();
            this.policy.scheduleTime = scheduleTime;
            return this;
        }

        public Builder monthly(int monthlyLogDay, String scheduleTime) {
            this.policy.scheduleType = ScheduleType.MONTHLY.getCode();
            this.policy.monthlyLogDay = monthlyLogDay;
            this.policy.scheduleTime = scheduleTime;
            return this;
        }

        public Builder addRule(Rule rule) {
            if (rule != null) {
                this.policy.rules.add(rule);
            }
            return this;
        }

        public JobPolicy build() {
            return this.policy;
        }
    }

    @Override
    public String toString() {
        return "JobPolicy{" +
                "jobNo='" + jobNo + '\'' +
                ", jobName='" + jobName + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", filePrefix='" + filePrefix + '\'' +
                ", scheduleType='" + scheduleType + '\'' +
                ", scheduleTime='" + scheduleTime + '\'' +
                ", rulesCount=" + rules.size() +
                '}';
    }
}
