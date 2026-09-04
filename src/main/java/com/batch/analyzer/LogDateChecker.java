package com.batch.analyzer;

import com.batch.config.Config;
import com.batch.model.JobPolicy;
import com.batch.model.LogConstants;
import com.batch.model.RuleResult;
import com.batch.model.RuleType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * =====================================================================================
 * [단일 책임 원칙 (SRP): 배치 로그 일자 검증기 (LogDateChecker)]
 * -------------------------------------------------------------------------------------
 * 💡 역할 및 책임:
 * 1. 로그 파일의 첫 타임스탬프(실제 실행 일시)를 추출합니다.
 * 2. 분석 대상 폴더명 또는 환경설정 기반으로 분석 기준일(BaseDate)을 결정합니다.
 * 3. 배치 정책(실행 시각 scheduleTime, 실행 주기 scheduleType) 및 
 *    분기 기준 시각(log.cutoff.time, 기본값: 09:05)에 따라 정상 기대 일자를 계산합니다:
 *    - 일간 배치: 09:05 이하 -> 당일(CURRENT_DAY), 09:05 초과 -> 전일(PREVIOUS_DAY)
 *    - 월간 배치: 매월 1일 실행 후 매월 2일 생성(MONTHLY, monthlyLogDay: 2)
 * 4. 기대 일자와 실제 로그 일자를 비교 검증하여 RuleResult를 반환합니다.
 * 5. 옵션(skipDateCheck=true 또는 log.date.check.enabled=false) 시 일자 점검을 건너뜁니다.
 * =====================================================================================
 */
public class LogDateChecker {

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2})");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 로그 파일 일자 검증 실행 (일급 객체 LogContent 지원)
     */
    public RuleResult checkDate(com.batch.model.LogContent logContent, JobPolicy policy, String folderName, File[] logFiles, boolean skipDateCheck) {
        String fullText = logContent != null ? logContent.getFullText() : null;
        return checkDate(fullText, policy, folderName, logFiles, skipDateCheck);
    }

    public RuleResult checkDate(com.batch.model.LogContent logContent, JobPolicy policy, String folderName, boolean skipDateCheck) {
        return checkDate(logContent, policy, folderName, null, skipDateCheck);
    }

    /**
     * 로그 파일 일자 검증 실행 (기본)
     */
    public RuleResult checkDate(String fullText, JobPolicy policy, String folderName, boolean skipDateCheck) {
        return checkDate(fullText, policy, folderName, null, skipDateCheck);
    }

    /**
     * 로그 파일 일자 검증 실행 (로그 파일 목록 포함)
     *
     * @param fullText       로그 파일 전체 텍스트
     * @param policy         JOB 정책
     * @param folderName     작업 폴더명 (날짜 해석용)
     * @param logFiles       폴더 내 로그 파일 배열 (기준일 자동 감지용)
     * @param skipDateCheck  일자 점검 건너뛰기 옵션 여부
     * @return 일자 검증 결과 RuleResult (ruleNo: "00")
     */
    public RuleResult checkDate(String fullText, JobPolicy policy, String folderName, File[] logFiles, boolean skipDateCheck) {
        RuleResult result = new RuleResult();
        result.ruleNo = LogConstants.DEFAULT_DATE_CHECK_RULE_NO;
        result.type = RuleType.DATE_CHECK.getCode();
        result.description = LogConstants.DATE_CHECK_DESCRIPTION;

        // 1. 로그 텍스트에서 첫 타임스탬프 추출
        LocalDateTime logDateTime = extractLogDateTime(fullText);
        String logDateTimeStr = extractLogDateTimeString(fullText);

        if (logDateTime == null) {
            result.extractedValue = "미발견";
            result.condition = "타임스탬프 확인";
            result.passed = false;
            result.message = "로그 파일에서 실행 일시(타임스탬프)를 추출할 수 없습니다.";
            return result;
        }

        result.extractedValue = logDateTimeStr;
        LocalDate actualLogDate = logDateTime.toLocalDate();

        // 2. 검증 비활성화 또는 스킵 옵션 처리
        boolean dateCheckEnabled = isDateCheckEnabled() && !skipDateCheck;
        if (!dateCheckEnabled) {
            result.passed = true;
            result.condition = "일자 점검 건너뜀 (전체 처리 옵션)";
            result.message = LogConstants.MSG_NORMAL_FILE_COLLECTED + " (점검생략)";
            return result;
        }

        // 3. 분석 기준일(Base Date) 결정
        LocalDate baseDate = resolveBaseDate(folderName, logFiles, actualLogDate);

        // 4. 정책 기반 기대 일자(Expected Date) 계산
        String cutoffTime = Config.get("log.cutoff.time", LogConstants.DEFAULT_CUTOFF_TIME);
        ExpectedDateInfo expectedInfo = calculateExpectedDate(policy, baseDate, cutoffTime);

        result.condition = expectedInfo.ruleDescription;

        // 5. 일치 여부 검증
        if (actualLogDate.equals(expectedInfo.expectedDate)) {
            result.passed = true;
            result.message = LogConstants.MSG_NORMAL_FILE_COLLECTED;
        } else {
            result.passed = false;
            result.message = String.format("로그 일자 불일치! 기대: %s (%s), 실제: %s",
                    expectedInfo.expectedDate.format(DATE_FORMATTER), expectedInfo.scheduleDesc, actualLogDate.format(DATE_FORMATTER));
        }

        return result;
    }

    /**
     * 로그 텍스트에서 첫 번째 타임스탬프(LocalDateTime) 파싱
     */
    public static LocalDateTime extractLogDateTime(String fullText) {
        if (fullText == null || fullText.isEmpty()) return null;
        Matcher m = TIMESTAMP_PATTERN.matcher(fullText);
        if (m.find()) {
            try {
                String datePart = m.group(1);
                String timePart = m.group(2);
                return LocalDateTime.parse(datePart + "T" + timePart);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 로그 텍스트에서 첫 번째 타임스탬프 원본 문자열 추출
     */
    public static String extractLogDateTimeString(String fullText) {
        if (fullText == null || fullText.isEmpty()) return null;
        Matcher m = TIMESTAMP_PATTERN.matcher(fullText);
        if (m.find()) {
            return m.group(0);
        }
        return null;
    }

    /**
     * 폴더명 및 로그 파일 목록으로부터 분석 기준일(BaseDate) 파싱 및 자동 감지
     */
    public static LocalDate resolveBaseDate(String folderName, File[] logFiles, LocalDate fallbackDate) {
        if (folderName != null) {
            String trimmed = folderName.trim();
            // 6자리 날짜 (예: 260822 -> 2026-08-22)
            if (trimmed.matches("\\d{6}")) {
                try {
                    int yy = Integer.parseInt(trimmed.substring(0, 2));
                    int mm = Integer.parseInt(trimmed.substring(2, 4));
                    int dd = Integer.parseInt(trimmed.substring(4, 6));
                    int yyyy = 2000 + yy;
                    return LocalDate.of(yyyy, mm, dd);
                } catch (Exception ignored) {}
            }
            // 8자리 날짜 (예: 20260822 -> 2026-08-22)
            if (trimmed.matches("\\d{8}")) {
                try {
                    int yyyy = Integer.parseInt(trimmed.substring(0, 4));
                    int mm = Integer.parseInt(trimmed.substring(4, 6));
                    int dd = Integer.parseInt(trimmed.substring(6, 8));
                    return LocalDate.of(yyyy, mm, dd);
                } catch (Exception ignored) {}
            }
        }

        // 폴더명이 날짜가 아닌 경우 로그 파일들에서 최신 날짜(MAX) 자동 감지
        LocalDate detectedFromFiles = detectBaseDateFromFiles(logFiles);
        if (detectedFromFiles != null) {
            return detectedFromFiles;
        }

        return fallbackDate != null ? fallbackDate : LocalDate.now();
    }

    public static LocalDate resolveBaseDate(String folderName, LocalDate fallbackDate) {
        return resolveBaseDate(folderName, null, fallbackDate);
    }

    /**
     * 로그 파일 배열에서 가장 최신 날짜(MAX)를 분석 기준일로 감지
     */
    public static LocalDate detectBaseDateFromFiles(File[] logFiles) {
        if (logFiles == null || logFiles.length == 0) return null;
        LocalDate maxDate = null;

        for (File file : logFiles) {
            if (file == null || !file.exists() || !file.getName().endsWith(".log")) continue;
            try {
                // 상위 30줄 정도만 빠르게 스캔
                String header = readHeader(file, 2048);
                LocalDateTime dt = extractLogDateTime(header);
                if (dt != null) {
                    LocalDate d = dt.toLocalDate();
                    if (maxDate == null || d.isAfter(maxDate)) {
                        maxDate = d;
                    }
                }
            } catch (Exception ignored) {}
        }
        return maxDate;
    }

    private static String readHeader(File file, int maxBytes) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            int len = Math.min(bytes.length, maxBytes);
            return new String(bytes, 0, len, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static class ExpectedDateInfo {
        public LocalDate expectedDate;
        public String scheduleDesc;
        public String ruleDescription;

        public ExpectedDateInfo(LocalDate expectedDate, String scheduleDesc, String ruleDescription) {
            this.expectedDate = expectedDate;
            this.scheduleDesc = scheduleDesc;
            this.ruleDescription = ruleDescription;
        }
    }

    /**
     * 정책 및 분기 기준 시각 기반 기대 일자 계산
     */
    public static ExpectedDateInfo calculateExpectedDate(JobPolicy policy, LocalDate baseDate, String cutoffTime) {
        if (baseDate == null) baseDate = LocalDate.now();
        if (cutoffTime == null || cutoffTime.isEmpty()) cutoffTime = "09:05";

        // 1. 월간 배치인 경우 (MONTHLY)
        if (policy != null && "MONTHLY".equalsIgnoreCase(policy.scheduleType)) {
            int targetDay = (policy.monthlyLogDay != null && policy.monthlyLogDay > 0) ? policy.monthlyLogDay : 2;
            int year = baseDate.getYear();
            int month = baseDate.getMonthValue();
            LocalDate expectedMonthly = LocalDate.of(year, month, Math.min(targetDay, baseDate.lengthOfMonth()));
            return new ExpectedDateInfo(expectedMonthly, "월간", "월간배치 (" + targetDay + "일자 로그)");
        }

        // 2. 일간 배치인 경우 (DAILY 또는 기본값)
        String scheduleTime = (policy != null && policy.scheduleTime != null) ? policy.scheduleTime.trim() : "";

        // scheduleTime이 09:05 이하인지 비교
        boolean isCurrentDay;
        if (!scheduleTime.isEmpty()) {
            isCurrentDay = scheduleTime.compareTo(cutoffTime) <= 0;
        } else {
            // scheduleTime이 없으면 기본 당일 처리
            isCurrentDay = true;
        }

        if (isCurrentDay) {
            LocalDate expected = baseDate;
            String timeDesc = scheduleTime.isEmpty() ? "<=" + cutoffTime : scheduleTime;
            return new ExpectedDateInfo(expected, "당일", "당일 실행 (" + timeDesc + " 기준)");
        } else {
            LocalDate expected = baseDate.minusDays(1);
            return new ExpectedDateInfo(expected, "전일", "전일자 실행 (" + scheduleTime + " > " + cutoffTime + " 기준)");
        }
    }

    private static boolean isDateCheckEnabled() {
        String enabled = Config.get("log.date.check.enabled", "true");
        return "true".equalsIgnoreCase(enabled) || "1".equals(enabled) || "yes".equalsIgnoreCase(enabled);
    }
}
