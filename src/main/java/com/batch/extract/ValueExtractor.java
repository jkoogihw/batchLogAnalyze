package com.batch.extract;

import com.batch.model.Rule;
import com.batch.model.StepMetrics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 값 추출 유틸리티
 * 
 * 로그 텍스트에서 다양한 형식의 값을 추출합니다.
 */
public class ValueExtractor {
    
    /**
     * DISPLAY 타입 규칙에서 값 추출
     */
    public static String extractDisplayValue(String fullText, String[] lines, Rule rule) {
        String target = rule.target;
        String regex = rule.regex;

        // 1. Regex가 명시적으로 지정된 경우
        if (regex != null && !regex.isEmpty()) {
            Pattern p = Pattern.compile(
                    regex + "\\s*([=:]\\s*|\\s+)?([0-9,]+(\\s*건)?)", 
                    Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(fullText);
            if (m.find()) {
                String val = m.group(2);
                if (val != null) return val.trim();
            }
        }

        // 2. 줄바꿈 포함 패턴 처리 (연속 공백/개행 정규화 매칭)
        String literalTarget = target.replace("\\n", "\n").replace("\\r", "\r");
        if (literalTarget.contains("\n") || literalTarget.contains("\r")) {
            String[] targetParts = literalTarget.split("[\\r\\n]+");

            String firstLineText = targetParts[0].trim();
            int startIndex = fullText.indexOf(firstLineText);
            if(startIndex != -1){
                int endIndex = Math.min(startIndex + 500, fullText.length());
                String subText = fullText.substring(startIndex, endIndex);
                
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < targetParts.length; i++) {
                    if (i > 0) sb.append("[\\s\\r\\n]*");
                    sb.append("\\s*").append(Pattern.quote(targetParts[i].trim())).append("\\s*");
                }
                sb.append(".*?[\\s:=]+([0-9,]+(\\s*건\\.?)?)");
                Pattern p = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                Matcher m = p.matcher(subText);
                if (m.find()) {
                    String val = m.group(1);
                    if (val != null) return val.trim();
                }
            }
        }

        // 3. 일반 단일행 또는 특수 포맷 검색
        for (String line : lines) {
            if (line.contains(target)) {
                int idx = line.indexOf(target) + target.length();
                String rest = line.substring(idx).trim();

                // 패턴: [{...}] : 1175건  (예: 파기목록 조회)
                if (rest.startsWith("[")) {
                    int closeBracket = rest.indexOf("]");
                    if (closeBracket != -1) {
                        rest = rest.substring(closeBracket + 1).trim();
                    }
                }

                // 패턴: : 0 / 피적용건수...  (예: FP누락)
                if (rest.startsWith(":")) {
                    rest = rest.substring(1).trim();
                } else if (rest.startsWith("=")) {
                    rest = rest.substring(1).trim();
                }

                // 앞의 숫자 추출
                Matcher nm = Pattern.compile("^([0-9,]+)(\\s*건)?").matcher(rest);
                if (nm.find()) {
                    return nm.group(0).trim();
                }

                // 패턴: 업데이트609건. 또는 대상 609건.
                Matcher nm2 = Pattern.compile("(업데이트|대상)?\\s*([0-9,]+\\s*건)").matcher(rest);
                if (nm2.find()) {
                    return nm2.group(2).trim();
                }

                // 만약 콜론 뒤에 단순 숫자가 오면
                Matcher nm3 = Pattern.compile("([0-9,]+)").matcher(rest);
                if (nm3.find()) {
                    return nm3.group(1).trim();
                }
            }
        }

        // 4. 멀티라인 전체 텍스트에서 재탐색
        String cleanKey = target.replaceAll("[\\r\\n\\s]+", " ").trim();
        String cleanFull = fullText.replaceAll("[\\r\\n]+", " ");
        int cIdx = cleanFull.indexOf(cleanKey);
        
        if (cIdx != -1) {
            String rest = cleanFull.substring(cIdx + cleanKey.length()).trim();
            if (rest.startsWith(":") || rest.startsWith("=")) {
                rest = rest.substring(1).trim();
            }
            Matcher nm = Pattern.compile("^([0-9,]+(\\s*건)?)").matcher(rest);
            if (nm.find()) {
                return nm.group(1).trim();
            }
        }

        // 5. 오류문자열 미확인 처리
        if("ERROR_IF_PRESENT".equalsIgnoreCase(rule.condition)){
            return "0";
        }

        return null;
    }

    /**
     * 추출된 값에서 숫자만 추출
     */
    public static Long parseNumber(String val) {
        if (val == null) return null;
        String clean = val.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) return null;
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Step 요약 통계 파싱
     */
    public static StepMetrics parseStepMetrics(String[] lines, String stepName) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("StepName : " + stepName)) {
                long readCount = 0;
                long writeCount = 0;
                long commitCount = 0;
                long rollbackCount = 0;

                for (int j = i + 1; j <= i + 6 && j < lines.length; j++) {
                    String l = lines[j];
                    if (l.contains("ReadCount")) {
                        readCount = extractMetricNumber(l, "ReadCount");
                    } else if (l.contains("WriteCount")) {
                        writeCount = extractMetricNumber(l, "WriteCount");
                    } else if (l.contains("CommitCount")) {
                        commitCount = extractMetricNumber(l, "CommitCount");
                    } else if (l.contains("RollbackCount")) {
                        rollbackCount = extractMetricNumber(l, "RollbackCount");
                    }
                }
                return StepMetrics.of(stepName, readCount, writeCount, commitCount, rollbackCount);
            }
        }
        return null;
    }

    /**
     * 로그 라인에서 메트릭 숫자 추출
     */
    public static long extractMetricNumber(String line, String keyword) {
        int idx = line.indexOf(keyword);
        if (idx != -1) {
            String rest = line.substring(idx + keyword.length());
            int colonIdx = rest.indexOf(":");
            if (colonIdx != -1) {
                rest = rest.substring(colonIdx + 1);
            }
            Matcher m = Pattern.compile("([0-9,]+)").matcher(rest);
            if (m.find()) {
                return Long.parseLong(m.group(1).replace(",", ""));
            }
        }
        return 0;
    }

    /**
     * SEARCH 타입 규칙에서 건수 계산
     */
    public static int countMatches(String fullText, Rule rule) {
        int count = 0;
        if (rule.regex != null && !rule.regex.isEmpty()) {
            Pattern p = Pattern.compile(rule.regex);
            Matcher m = p.matcher(fullText);
            while (m.find()) count++;
        } else {
            int idx = 0;
            while ((idx = fullText.indexOf(rule.target, idx)) != -1) {
                count++;
                idx += rule.target.length();
            }
        }
        return count;
    }
}
