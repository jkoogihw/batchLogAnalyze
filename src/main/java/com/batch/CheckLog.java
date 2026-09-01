package com.batch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * 배치로그 분석 및 정상 여부 검증 프로그램
 * 
 * - 'policy_meta.json' 기반 정책 검증 (application.properties 설정)
 * - 검색(전체 발생 건수), 표시(키워드 뒤 건수 추출), 멀티라인 텍스트 매칭 지원
 * - 비영업일 예외(4번, 5번 등) 및 Spring Batch Step 요약 통계(RollbackCount) 검증
 * - 콘솔 출력 및 마크다운 리포트 자동 생성
 */
public class CheckLog {
    
    // 프로젝트 설정 파일에서 로드
    private static final Properties config = loadConfig();
    
    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream input = CheckLog.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.err.println("[오류] application.properties 파일을 찾을 수 없습니다.");
                System.exit(1);
            }
            props.load(input);
        } catch (IOException e) {
            System.err.println("[오류] application.properties 로드 실패: " + e.getMessage());
            System.exit(1);
        }
        return props;
    }
    
    private static final String BASE_FOLDER = config.getProperty("base.folder");
    private static final String LOG_ANALYSIS_DIR = config.getProperty("log.analysis.dir");
    private static final String POLICY_META_FILE = config.getProperty("policy.meta.file");
    private static final String META_PATH = BASE_FOLDER + File.separator + LOG_ANALYSIS_DIR + File.separator + POLICY_META_FILE;
    private static final String REPORT_DIR = BASE_FOLDER + File.separator + LOG_ANALYSIS_DIR;

    // 정책 모델 클래스 정의
    public static class JobPolicy {
        public String jobNo;
        public String jobName;
        public String jobTitle;
        public String filePrefix;
        public String rawPattern;
        public String holidayPattern;
        public List<Rule> rules = new ArrayList<>();
    }

    public static class Rule {
        public String type; // DISPLAY, SEARCH, STEP_METRICS
        public String target;
        public String regex;
        public String stepName;
        public String condition; // EQUALS_0, EQUALS_N, COUNT_CHECK, ROLLBACK_ZERO, ERROR_IF_PRESENT
        public int expectedCount = 0;
        public String description;
    }

    public static class CheckResult {
        public String jobNo;
        public String jobName;
        public String jobTitle;
        public String fileName;
        public boolean fileFound = false;
        public boolean isHoliday = false;
        public String holidayDetail = "";
        public List<RuleResult> ruleResults = new ArrayList<>();
        public boolean overallPassed = true;
    }

    public static class RuleResult {
        public String description;
        public String type;
        public String target;
        public String extractedValue;
        public String condition;
        public boolean passed;
        public String message;
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  [배치 로그 자동 분석 및 정상 여부 검증 프로그램 (CheckLog)]");
        System.out.println("================================================================================");

        // 1. 대상 폴더 결정
        String targetFolder = args.length > 0 ? args[0] : "";
        File workFolder = targetFolder.isEmpty() ? getLatestDateFolder(BASE_FOLDER) : new File(BASE_FOLDER, targetFolder);

        if (workFolder == null || !workFolder.exists()) {
            System.err.println("[오류] 지정한 로그 대상 폴더가 존재하지 않습니다: " + workFolder);
            return;
        }

        String folderName = workFolder.getName();
        System.out.println(">> 분석 대상 폴더: " + workFolder.getAbsolutePath() + " (" + folderName + ")");

        // 2. 정책 메타데이터 로드
        List<JobPolicy> policies = loadPolicies();
        System.out.println(">> 로드된 배치 정책 수: " + policies.size() + "개 JOB");

        // 3. 로그 파일 검색 및 검증 수행
        File[] logFiles = workFolder.listFiles((dir, name) -> name.endsWith(".log"));
        if (logFiles == null) logFiles = new File[0];

        List<CheckResult> results = new ArrayList<>();
        int totalJobs = policies.size();
        int passCount = 0;
        int failCount = 0;

        for (JobPolicy policy : policies) {
            CheckResult cr = checkJob(workFolder, logFiles, policy);
            results.add(cr);
            if (cr.overallPassed) {
                passCount++;
            } else {
                failCount++;
            }
        }

        // 4. 콘솔 결과 출력
        printConsoleReport(folderName, results, totalJobs, passCount, failCount);

        // 5. 마크다운 리포트 파일 생성
        saveMarkdownReport(folderName, results, totalJobs, passCount, failCount);
    }

    /**
     * 개별 JOB 로그 검증 수행
     */
    private static CheckResult checkJob(File workFolder, File[] logFiles, JobPolicy policy) {
        CheckResult cr = new CheckResult();
        cr.jobNo = policy.jobNo;
        cr.jobName = policy.jobName;
        cr.jobTitle = policy.jobTitle;

        // 파일 매핑 (접두사 또는 원본 파일명 패턴)
        File targetFile = findTargetFile(logFiles, policy);

        if (targetFile == null) {
            cr.fileFound = false;
            cr.fileName = policy.filePrefix + "*.log (미발견)";
            cr.overallPassed = false;
            RuleResult rr = new RuleResult();
            rr.description = "로그 파일 존재 여부";
            rr.passed = false;
            rr.message = "해당 JOB의 로그 파일이 존재하지 않습니다.";
            cr.ruleResults.add(rr);
            return cr;
        }

        cr.fileFound = true;
        cr.fileName = targetFile.getName();

        try {
            // 파일 읽기 (UTF-8)
            String fullText = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
            String[] lines = fullText.split("\\r?\\n");

            // 비영업일 예외 검사 (4번, 5번 등)
            if (policy.holidayPattern != null && !policy.holidayPattern.isEmpty()) {
                Pattern hp = Pattern.compile(policy.holidayPattern);
                Matcher hm = hp.matcher(fullText);
                if (hm.find()) {
                    cr.isHoliday = true;
                    cr.holidayDetail = hm.group(0);
                    cr.overallPassed = true;
                    RuleResult rr = new RuleResult();
                    rr.description = "비영업일 예외 확인";
                    rr.type = "HOLIDAY";
                    rr.target = policy.holidayPattern;
                    rr.extractedValue = cr.holidayDetail;
                    rr.condition = "비영업일 수행 건너뜀 (정상)";
                    rr.passed = true;
                    rr.message = "비영업일 안내 로그 감지됨 -> 정상 판정";
                    cr.ruleResults.add(rr);
                    return cr;
                }
            }

            // 개별 규칙 검증
            for (Rule rule : policy.rules) {
                RuleResult rr = evaluateRule(fullText, lines, rule);
                cr.ruleResults.add(rr);
                if (!rr.passed) {
                    cr.overallPassed = false;
                }
            }

        } catch (Exception e) {
            cr.overallPassed = false;
            RuleResult rr = new RuleResult();
            rr.description = "파일 분석 중 오류 발생";
            rr.passed = false;
            rr.message = "오류 내용: " + e.getMessage();
            cr.ruleResults.add(rr);
        }

        return cr;
    }

    /**
     * 단일 규칙 평가 로직
     */
    private static RuleResult evaluateRule(String fullText, String[] lines, Rule rule) {
        RuleResult rr = new RuleResult();
        rr.description = rule.description != null ? rule.description : rule.target;
        rr.type = rule.type;
        rr.target = rule.target;
        rr.condition = rule.condition;

        if ("SEARCH".equalsIgnoreCase(rule.type)) {
            // 전체 텍스트 검색 건수 집계
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
            rr.extractedValue = count + "건";

            if ("EQUALS_N".equalsIgnoreCase(rule.condition)) {
                rr.passed = (count == rule.expectedCount);
                rr.message = rr.passed ? "정상 (" + count + "건 일치)" : "불일치 (기대: " + rule.expectedCount + "건, 실제: " + count + "건)";
            } else if ("COUNT_CHECK".equalsIgnoreCase(rule.condition)) {
                rr.passed = true; // 검색 건수 확인 성공
                rr.message = "건수확인: " + count + "건";
            } else if ("EQUALS_0".equalsIgnoreCase(rule.condition)) {
                rr.passed = (count == 0);
                rr.message = rr.passed ? "정상 (0건)" : "오류 (" + count + "건 발생)";
            }

        } else if ("DISPLAY".equalsIgnoreCase(rule.type)) {
            // 키워드 다음 건수 추출
            String foundSnippet = extractDisplayValue(fullText, lines, rule);

            if (foundSnippet != null) {
                rr.extractedValue = foundSnippet;
                Long numVal = parseNumber(foundSnippet);

                if ("EQUALS_0".equalsIgnoreCase(rule.condition)) {
                    if (numVal != null) {
                        rr.passed = (numVal == 0);
                        rr.message = rr.passed ? "정상 (0건)" : "오류 (추출값: " + foundSnippet + ")";
                    } else {
                        rr.passed = false;
                        rr.message = "숫자 파싱 실패 (" + foundSnippet + ")";
                    }
                } else if ("ERROR_IF_PRESENT".equalsIgnoreCase(rule.condition)) {
                    // SmpmSkipPolicy 등
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

        } else if ("STEP_METRICS".equalsIgnoreCase(rule.type)) {
            // StepName 블록 요약 통계 검증 (ReadCount, WriteCount, CommitCount, RollbackCount)
            String stepName = rule.stepName;
            StepMetrics metrics = parseStepMetrics(lines, stepName);

            if (metrics != null) {
                rr.extractedValue = String.format("R:%d / W:%d / C:%d / Rollback:%d", 
                        metrics.readCount, metrics.writeCount, metrics.commitCount, metrics.rollbackCount);
                if ("ROLLBACK_ZERO".equalsIgnoreCase(rule.condition)) {
                    rr.passed = (metrics.rollbackCount == 0);
                    rr.message = rr.passed ? "정상 (Rollback 0건)" : "오류 (RollbackCount: " + metrics.rollbackCount + "건 발생!)";
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

        return rr;
    }

    /**
     * 표시(DISPLAY) 타입 건수 추출 함수
     */
    private static String extractDisplayValue(String fullText, String[] lines, Rule rule) {
        String target = rule.target;
        String regex = rule.regex;

        // 1. Regex가 명시적으로 지정되어 있는 경우
        if (regex != null && !regex.isEmpty()) {
            Pattern p = Pattern.compile(regex + "\\s*([=:]\\s*|\\s+)?([0-9,]+(\\s*건)?)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(fullText);
            if (m.find()) {
                String val = m.group(2);
                if (val != null) return val.trim();
            }
        }

        // 2. 줄바꿈 포함 패턴 처리 (연속 공백/개행 정규화 매칭)
        if (target.contains("\n") || target.contains("\r")) {
            String[] targetParts = target.split("[\\r\\n]+");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < targetParts.length; i++) {
                if (i > 0) sb.append("[\\s\\r\\n]*");
                sb.append(Pattern.quote(targetParts[i].trim()));
            }
            sb.append("\\s*[:=]?\\s*([0-9,]+(\\s*건)?)");
            Pattern p = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(fullText);
            if (m.find()) {
                String val = m.group(1);
                if (val != null) return val.trim();
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
            if (rest.startsWith(":") || rest.startsWith("=")) rest = rest.substring(1).trim();
            Matcher nm = Pattern.compile("^([0-9,]+(\\s*건)?)").matcher(rest);
            if (nm.find()) {
                return nm.group(1).trim();
            }
        }

        return null;
    }

    private static Long parseNumber(String val) {
        if (val == null) return null;
        String clean = val.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) return null;
        try {
            return Long.parseLong(clean);
        } catch (Exception e) {
            return null;
        }
    }

    public static class StepMetrics {
        public long readCount = 0;
        public long writeCount = 0;
        public long commitCount = 0;
        public long rollbackCount = 0;
    }

    private static StepMetrics parseStepMetrics(String[] lines, String stepName) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("StepName : " + stepName)) {
                StepMetrics sm = new StepMetrics();
                for (int j = i + 1; j <= i + 6 && j < lines.length; j++) {
                    String l = lines[j];
                    if (l.contains("ReadCount")) sm.readCount = extractMetricNumber(l, "ReadCount");
                    else if (l.contains("WriteCount")) sm.writeCount = extractMetricNumber(l, "WriteCount");
                    else if (l.contains("CommitCount")) sm.commitCount = extractMetricNumber(l, "CommitCount");
                    else if (l.contains("RollbackCount")) sm.rollbackCount = extractMetricNumber(l, "RollbackCount");
                }
                return sm;
            }
        }
        return null;
    }

    private static long extractMetricNumber(String line, String keyword) {
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
     * 콘솔 요약 결과 출력
     */
    private static void printConsoleReport(String folderName, List<CheckResult> results, int total, int pass, int fail) {
        System.out.println("\n" + "=".repeat(105));
        System.out.println(String.format(" [%s] 배치로그 분석 종합 결과 요약", folderName));
        System.out.println("=".repeat(105));
        System.out.println(String.format(" >> 전체 대상: %d 건 | 정상(PASS): %d 건 | 오류/확인필요(FAIL): %d 건", total, pass, fail));
        System.out.println("-".repeat(105));

        System.out.println(String.format("%-4s | %-48s | %-8s | %s", "No", "JOB 명칭 / 항목", "상태", "추출값 / 상세내용"));
        System.out.println("-".repeat(105));

        for (CheckResult cr : results) {
            String statusBadge = cr.overallPassed ? "[ PASS ]" : "[ FAIL ]";
            System.out.println(String.format("%-4s | %-48s | %-8s | 파일: %s", 
                    cr.jobNo, truncate(cr.jobTitle, 48), statusBadge, cr.fileName));

            if (cr.isHoliday) {
                System.out.println(String.format("     |   %-46s | %-8s | %s", 
                        "-> [비영업일 예외]", "[ PASS ]", cr.holidayDetail));
            } else {
                for (RuleResult rr : cr.ruleResults) {
                    String rStatus = rr.passed ? "  OK  " : " FAIL ";
                    System.out.println(String.format("     |   %-46s | %-8s | 추출: %-18s (%s)", 
                            truncate("-> " + rr.description, 46), rStatus, rr.extractedValue != null ? rr.extractedValue : "-", rr.message));
                }
            }
            System.out.println("-".repeat(105));
        }
    }

    /**
     * 마크다운 리포트 파일 생성
     */
    private static void saveMarkdownReport(String folderName, List<CheckResult> results, int total, int pass, int fail) {
        File dir = new File(REPORT_DIR);
        if (!dir.exists()) dir.mkdirs();

        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String reportFileName = "로그분석결과_" + folderName + ".md";
        File reportFile = new File(dir, reportFileName);

        StringBuilder sb = new StringBuilder();
        sb.append("# 배치로그 분석 결과 보고서 (").append(folderName).append(")\n\n");
        sb.append("- **분석 일시**: ").append(timeStamp).append("\n");
        sb.append("- **대상 폴더**: `배치로그\\").append(folderName).append("`\n");
        sb.append("- **전체 결과**: 총 **").append(total).append("**개 JOB 중 **")
          .append(pass).append("**개 정상 (PASS), **")
          .append(fail).append("**개 오류/확인필요 (FAIL)\n\n");

        sb.append("## 1. JOB별 세부 분석 내역\n\n");
        sb.append("| 번호 | JOB ID | JOB 이름 | 점검항목 | 점검내용 | 점검결과 |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- | :---: |\n");

        for (CheckResult cr : results) {
            String statusText = cr.overallPassed ? "✅ 정상" : "❌ 오류";
            String cleanedJobTitle = removeJobNamePrefix(cr.jobName, cr.jobTitle);
            
            if (cr.isHoliday) {
                sb.append(String.format("| %s | %s | %s | 비영업일 예외 | %s | %s |\n",
                        cr.jobNo, cr.jobName, cleanedJobTitle, "", cr.holidayDetail, statusText));
            } else {
                boolean first = true;
                for (RuleResult rr : cr.ruleResults) {
                    String ruleStatus = rr.passed ? "✅" : "❌";
                    String checkItem = "**" + rr.description + "**<br/>`" + (rr.extractedValue != null ? rr.extractedValue : "-") + "`";
                    String checkContent = ruleStatus + " " + rr.message;
                    if (first) {
                        sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                                cr.jobNo, cr.jobName, cleanedJobTitle, checkItem, checkContent, statusText));
                        first = false;
                    } else {
                        sb.append(String.format("| | | | %s | %s | |\n",
                                checkItem, checkContent));
                    }
                }
            }
        }

        sb.append("\n## 2. 특이사항 및 참고\n");
        sb.append("- 비영업일 실행 시 smrmJob102, smrmJob103은 비영업일 안내 메시지 감지 시 정상 처리됩니다.\n");
        sb.append("- RollbackCount 통계는 0건일 때 정상으로 판정됩니다.\n");

        try {
            Files.writeString(reportFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
            System.out.println("\n>> 마크다운 리포트가 저장되었습니다: " + reportFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[경고] 리포트 파일 저장 실패: " + e.getMessage());
        }
    }

    private static String removeJobNamePrefix(String jobName, String jobTitle) {
        if (jobName == null || jobTitle == null) return jobTitle;
        if (jobTitle.startsWith(jobName)) {
            String result = jobTitle.substring(jobName.length()).trim();
            return result.isEmpty() ? jobTitle : result;
        }
        return jobTitle;
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }

    private static File findTargetFile(File[] logFiles, JobPolicy policy) {
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
                    String suffix = parts[1];
                    if (name.contains(basePattern)) {
                        String nameWithoutExt = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
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

    private static File getLatestDateFolder(String parentPath) {
        File parent = new File(parentPath);
        File[] dirs = parent.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return null;

        return Arrays.stream(dirs)
                .filter(d -> d.getName().matches("\\d{6}"))
                .max(Comparator.comparingInt(d -> Integer.parseInt(d.getName())))
                .orElse(null);
    }

    /**
     * 메타데이터 파일 로드 (필수, 없으면 실패)
     */
    private static List<JobPolicy> loadPolicies() {
        File metaFile = new File(META_PATH);
        if (!metaFile.exists()) {
            System.err.println("[오류] 정책 메타데이터 파일이 존재하지 않습니다: " + META_PATH);
            System.err.println("[안내] application.properties에서 경로 설정을 확인하세요.");
            System.exit(1);
        }
        
        try {
            String json = Files.readString(metaFile.toPath(), StandardCharsets.UTF_8);
            List<JobPolicy> parsed = parseJsonPolicies(json);
            if (parsed == null || parsed.isEmpty()) {
                System.err.println("[오류] 정책 파일에서 유효한 정책을 찾을 수 없습니다: " + META_PATH);
                System.exit(1);
            }
            return parsed;
        } catch (Exception e) {
            System.err.println("[오류] 정책 메타데이터 파일 로드/파싱 실패: " + e.getMessage());
            System.exit(1);
        }
        return new ArrayList<>();
    }

    /**
     * 경량 내장 JSON 정책 파서
     */
    private static List<JobPolicy> parseJsonPolicies(String json) {
        List<JobPolicy> list = new ArrayList<>();
        Pattern jobPattern = Pattern.compile("\\{\\s*\"jobNo\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"jobName\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"jobTitle\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"filePrefix\"\\s*:\\s*\"([^\"]+)\"(.*?)\"rules\"\\s*:\\s*\\[(.*?)\\]\\s*\\}", Pattern.DOTALL);
        Matcher m = jobPattern.matcher(json);

        while (m.find()) {
            JobPolicy jp = new JobPolicy();
            jp.jobNo = m.group(1);
            jp.jobName = m.group(2);
            jp.jobTitle = m.group(3);
            jp.filePrefix = m.group(4);

            String mid = m.group(5);
            jp.rawPattern = extractJsonField(mid, "rawPattern");

            if (mid.contains("\"holidayCheck\"")) {
                Matcher hm = Pattern.compile("\"pattern\"\\s*:\\s*\"([^\"]+)\"").matcher(mid);
                if (hm.find()) {
                    jp.holidayPattern = hm.group(1).replace("\\\\", "\\");
                }
            }

            String rulesJson = m.group(6);
            Pattern rPat = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
            Matcher rm = rPat.matcher(rulesJson);
            while (rm.find()) {
                String rBlock = rm.group(1);
                Rule r = new Rule();
                r.type = extractJsonField(rBlock, "type");
                r.target = extractJsonField(rBlock, "target");
                if (r.target != null) r.target = r.target.replace("\\n", "\n");
                r.regex = extractJsonField(rBlock, "regex");
                if (r.regex != null) r.regex = r.regex.replace("\\\\", "\\");
                r.stepName = extractJsonField(rBlock, "stepName");
                r.condition = extractJsonField(rBlock, "condition");
                r.description = extractJsonField(rBlock, "description");
                String exp = extractJsonField(rBlock, "expectedCount");
                if (exp != null && !exp.isEmpty()) {
                    try { r.expectedCount = Integer.parseInt(exp); } catch (Exception ignored) {}
                }
                jp.rules.add(r);
            }
            list.add(jp);
        }

        return list;
    }

    private static String extractJsonField(String block, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1);

        Pattern pNum = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)");
        Matcher mNum = pNum.matcher(block);
        if (mNum.find()) return mNum.group(1);

        return null;
    }
}
