package com.batch.analyzer;

import com.batch.model.*;
import com.batch.extract.ValueExtractor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그 분석기
 * 
 * JOB 정책에 따라 로그 파일을 검증하고 검증 결과를 생성합니다.
 */
public class LogAnalyzer {
    
    /**
     * 개별 JOB 로그 검증 수행
     */
    public static CheckResult checkJob(File workFolder, File[] logFiles, JobPolicy policy) {
        CheckResult cr = new CheckResult(policy.jobNo, policy.jobName, policy.jobTitle);

        // 파일 매핑
        File targetFile = findTargetFile(logFiles, policy);

        if (targetFile == null) {
            cr.fileFound = false;
            cr.fileName = policy.filePrefix + "*.log (미발견)";
            cr.overallPassed = false;
            
            RuleResult rr = new RuleResult();
            rr.description = "로그 파일 존재 여부";
            rr.passed = false;
            rr.message = "해당 JOB의 로그 파일이 존재하지 않습니다.";
            cr.addRuleResult(rr);
            return cr;
        }

        cr.fileFound = true;
        cr.fileName = targetFile.getName();

        try {
            // 파일 읽기 (UTF-8)
            String fullText = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
            String[] lines = fullText.split("\\r?\\n");

            // 비영업일 예외 검사
            if (policy.holidayPattern != null && !policy.holidayPattern.isEmpty()) {
                Pattern hp = Pattern.compile(policy.holidayPattern);
                Matcher hm = hp.matcher(fullText);
                if (hm.find()) {
                    String holidayDetail = hm.group(0);
                    cr.markAsHoliday(holidayDetail);
                    
                    RuleResult rr = new RuleResult();
                    rr.description = "비영업일 예외 확인";
                    rr.type = "HOLIDAY";
                    rr.target = policy.holidayPattern;
                    rr.extractedValue = holidayDetail;
                    rr.condition = "비영업일 수행 건너뜀 (정상)";
                    rr.passed = true;
                    rr.message = "비영업일 안내 로그 감지됨 -> 정상 판정";
                    cr.addRuleResult(rr);
                    return cr;
                }
            }

            // 개별 규칙 검증
            for (Rule rule : policy.rules) {
                RuleResult rr = evaluateRule(fullText, lines, rule);
                cr.addRuleResult(rr);
            }

        } catch (Exception e) {
            cr.overallPassed = false;
            RuleResult rr = new RuleResult();
            rr.description = "파일 분석 중 오류 발생";
            rr.passed = false;
            rr.message = "오류 내용: " + e.getMessage();
            cr.addRuleResult(rr);
        }

        return cr;
    }

    /**
     * 단일 규칙 평가 로직
     */
    public static RuleResult evaluateRule(String fullText, String[] lines, Rule rule) {
        RuleResult rr = new RuleResult();
        rr.description = rule.description != null ? rule.description : rule.target;
        rr.type = rule.type;
        rr.target = rule.target;
        rr.condition = rule.condition;

        if ("SEARCH".equalsIgnoreCase(rule.type)) {
            evaluateSearchRule(fullText, rule, rr);
        } else if ("DISPLAY".equalsIgnoreCase(rule.type)) {
            evaluateDisplayRule(fullText, lines, rule, rr);
        } else if ("STEP_METRICS".equalsIgnoreCase(rule.type)) {
            evaluateStepMetricsRule(lines, rule, rr);
        }

        return rr;
    }

    /**
     * SEARCH 규칙 평가 (전체 텍스트 검색 건수)
     */
    private static void evaluateSearchRule(String fullText, Rule rule, RuleResult rr) {
        int count = ValueExtractor.countMatches(fullText, rule);
        rr.extractedValue = count + "건";

        if ("EQUALS_N".equalsIgnoreCase(rule.condition)) {
            rr.passed = (count == rule.expectedCount);
            rr.message = rr.passed ? 
                    "정상 (" + count + "건 일치)" : 
                    "불일치 (기대: " + rule.expectedCount + "건, 실제: " + count + "건)";
        } else if ("COUNT_CHECK".equalsIgnoreCase(rule.condition)) {
            rr.passed = true;
            rr.message = "건수확인: " + count + "건";
        } else if ("EQUALS_0".equalsIgnoreCase(rule.condition)) {
            rr.passed = (count == 0);
            rr.message = rr.passed ? "정상 (0건)" : "오류 (" + count + "건 발생)";
        }
    }

    /**
     * DISPLAY 규칙 평가 (키워드 다음 건수 추출)
     */
    private static void evaluateDisplayRule(String fullText, String[] lines, Rule rule, RuleResult rr) {
        String foundSnippet = ValueExtractor.extractDisplayValue(fullText, lines, rule);

        if (foundSnippet != null) {
            rr.extractedValue = foundSnippet;
            Long numVal = ValueExtractor.parseNumber(foundSnippet);

            if ("EQUALS_0".equalsIgnoreCase(rule.condition)) {
                if (numVal != null) {
                    rr.passed = (numVal == 0);
                    rr.message = rr.passed ? 
                            "정상 (0건)" : 
                            "오류 (추출값: " + foundSnippet + ")";
                } else {
                    rr.passed = false;
                    rr.message = "숫자 파싱 실패 (" + foundSnippet + ")";
                }
            } else if ("ERROR_IF_PRESENT".equalsIgnoreCase(rule.condition)) {
                if (numVal != null && numVal > 0) {
                    rr.passed = false;
                    rr.message = "오류 (건수 존재: " + foundSnippet + ")";
                } else {
                    rr.passed = true;
                    rr.message = "정상 (" + foundSnippet + ")";
                }
            } else if ("COUNT_CHECK".equalsIgnoreCase(rule.condition)) {
                rr.passed = true;
                rr.message = "건수확인: " + foundSnippet;
            }
        } else {
            if ("ERROR_IF_PRESENT".equalsIgnoreCase(rule.condition)) {
                rr.extractedValue = "0건 (미발생)";
                rr.passed = true;
                rr.message = "정상 (미발생)";
            } else {
                rr.extractedValue = "미발견";
                rr.passed = false;
                rr.message = "로그에서 해당 표시 패턴을 찾지 못했습니다.";
            }
        }
    }

    /**
     * STEP_METRICS 규칙 평가 (Step 통계)
     */
    private static void evaluateStepMetricsRule(String[] lines, Rule rule, RuleResult rr) {
        String stepName = rule.stepName;
        StepMetrics metrics = ValueExtractor.parseStepMetrics(lines, stepName);

        if (metrics != null) {
            rr.extractedValue = metrics.toDisplayString();
            if ("ROLLBACK_ZERO".equalsIgnoreCase(rule.condition)) {
                rr.passed = (metrics.rollbackCount == 0);
                rr.message = rr.passed ? 
                        "정상 (Rollback 0건)" : 
                        "오류 (RollbackCount: " + metrics.rollbackCount + "건 발생!)";
            } else {
                rr.passed = true;
                rr.message = "통계확인";
            }
        } else {
            rr.extractedValue = "미발견";
            rr.passed = false;
            rr.message = "StepName : " + stepName + " 블록을 찾지 못했습니다.";
        }
    }

    /**
     * 대상 로그 파일 찾기
     */
    public static File findTargetFile(File[] logFiles, JobPolicy policy) {
        if (logFiles == null) return null;
        
        // 1. 접두사 매칭 우선
        for (File f : logFiles) {
            if (f.getName().startsWith(policy.filePrefix)) {
                return f;
            }
        }
        
        // 2. 미변경 원본 파일명 패턴 매칭 (fallback)
        if (policy.rawPattern != null && !policy.rawPattern.isEmpty()) {
            for (File f : logFiles) {
                String name = f.getName();
                if (policy.rawPattern.contains("%")) {
                    String[] parts = policy.rawPattern.split("%");
                    String basePattern = parts[0];
                    String suffix = parts.length > 1 ? parts[1] : "";
                    if (name.contains(basePattern)) {
                        String nameWithoutExt = name.contains(".") ? 
                                name.substring(0, name.lastIndexOf('.')) : name;
                        if (nameWithoutExt.endsWith(suffix)) {
                            return f;
                        }
                    }
                } else if (name.contains(policy.rawPattern)) {
                    return f;
                }
            }
        }
        return null;
    }
}
