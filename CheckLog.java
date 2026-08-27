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
 * - '로그분석정책.md' 및 'policy_meta.json' 기반 정책 검증
 * - 검색(전체 발생 건수), 표시(키워드 뒤 건수 추출), 멀티라인 텍스트 매칭 지원
 * - 비영업일 예외(4번, 5번 등) 및 Spring Batch Step 요약 통계(RollbackCount) 검증
 * - 콘솔 출력 및 마크다운 리포트 자동 생성
 */
public class CheckLog {

    private static final String BASE_FOLDER = "D:\\job\\hw\\배치로그";
    private static final String META_PATH = BASE_FOLDER + "\\_로그분석\\policy_meta.json";
    private static final String REPORT_DIR = BASE_FOLDER + "\\_로그분석";

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
        sb.append("| 번호 | JOB 명칭 | 정상여부 | 세부 항목 및 추출 건수 | 판정 메시지 |\n");
        sb.append("| :--- | :--- | :---: | :--- | :--- |\n");

        for (CheckResult cr : results) {
            String statusText = cr.overallPassed ? "✅ **정상**" : "❌ **오류**";
            if (cr.isHoliday) {
                sb.append(String.format("| %s | %s | %s | 비영업일 예외 안내로그 확인 | %s |\n",
                        cr.jobNo, cr.jobTitle, statusText, cr.holidayDetail));
            } else {
                boolean first = true;
                for (RuleResult rr : cr.ruleResults) {
                    String ruleStatus = rr.passed ? "✅ OK" : "❌ FAIL";
                    if (first) {
                        sb.append(String.format("| %s | %s | %s | **%s**: `%s` | %s (%s) |\n",
                                cr.jobNo, cr.jobTitle, statusText, rr.description, rr.extractedValue, ruleStatus, rr.message));
                        first = false;
                    } else {
                        sb.append(String.format("| | | | **%s**: `%s` | %s (%s) |\n",
                                rr.description, rr.extractedValue, ruleStatus, rr.message));
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
     * 메타데이터 파일 로드 (부재 시 기본 내장 정책 Fallback)
     */
    private static List<JobPolicy> loadPolicies() {
        File metaFile = new File(META_PATH);
        if (metaFile.exists()) {
            try {
                String json = Files.readString(metaFile.toPath(), StandardCharsets.UTF_8);
                List<JobPolicy> parsed = parseJsonPolicies(json);
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            } catch (Exception e) {
                System.err.println("[알림] policy_meta.json 파싱 실패, 내장 기본 정책을 사용합니다: " + e.getMessage());
            }
        }
        return getFallbackPolicies();
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

        return list.isEmpty() ? getFallbackPolicies() : list;
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

    /**
     * 내장 Fallback 기본 정책 (JSON이 없을 때 자동 동작)
     */
    private static List<JobPolicy> getFallbackPolicies() {
        List<JobPolicy> list = new ArrayList<>();

        // 1. gagastJob002
        JobPolicy j1 = new JobPolicy();
        j1.jobNo = "01"; j1.jobName = "gagastJob002"; j1.jobTitle = "gagastJob002 추천터치고객 통계 데이터 적립"; j1.filePrefix = "01_gagastJob002_"; j1.rawPattern = "_10702_";
        Rule r1 = new Rule(); r1.type = "DISPLAY"; r1.target = "DB Insert GA Count"; r1.condition = "EQUALS_0"; r1.description = "DB Insert GA Count 0건 체크";
        j1.rules.add(r1);
        list.add(j1);

        // 2. GagastJob001
        JobPolicy j2 = new JobPolicy();
        j2.jobNo = "02"; j2.jobName = "GagastJob001"; j2.jobTitle = "GagastJob001 바른활동 실적 통계 데이터 적립"; j2.filePrefix = "02_gagastJob001_"; j2.rawPattern = "_10701_";
        Rule r2_1 = new Rule(); r2_1.type = "DISPLAY"; r2_1.target = "DB Insert GA Count"; r2_1.condition = "COUNT_CHECK"; r2_1.description = "DB Insert GA Count 건수확인";
        Rule r2_2 = new Rule(); r2_2.type = "DISPLAY"; r2_2.target = "# FP누락 활동 대상[활동건수"; r2_2.condition = "EQUALS_0"; r2_2.description = "# FP누락 활동 대상[활동건수 0건 체크";
        j2.rules.add(r2_1); j2.rules.add(r2_2);
        list.add(j2);

        // 3. smrmJob101
        JobPolicy j3 = new JobPolicy();
        j3.jobNo = "03"; j3.jobName = "smrmJob101"; j3.jobTitle = "smrmJob101 불완전판매"; j3.filePrefix = "03_smrmJob101_"; j3.rawPattern = "_11399_";
        Rule r3 = new Rule(); r3.type = "DISPLAY"; r3.target = "불완전판매조사 대상 신규 추출 건 [TB_SMRM1010]"; r3.condition = "COUNT_CHECK"; r3.description = "불완전판매조사 대상 신규 추출 건 [TB_SMRM1010]";
        j3.rules.add(r3);
        list.add(j3);

        // 4. smrmJob102
        JobPolicy j4 = new JobPolicy();
        j4.jobNo = "04"; j4.jobName = "smrmJob102"; j4.jobTitle = "[전일] smrmJob102 불판 확인 SMS 발송(FP?)"; j4.filePrefix = "04_smrmJob102_"; j4.rawPattern = "_11401_";
        j4.holidayPattern = "비영업일에는 해당 JOB이 (수행|수혈)되지 않습니다.";
        Rule r4_1 = new Rule(); r4_1.type = "DISPLAY"; r4_1.target = "불완전판매 SMS 발송불가 상태로 전환된 건\n- TB_SMRM1010"; r4_1.condition = "COUNT_CHECK"; r4_1.description = "불완전판매 SMS 발송불가 건";
        Rule r4_2 = new Rule(); r4_2.type = "DISPLAY"; r4_2.target = "불완전판매 SMS 발송 파일생성중 상태로 전환된 건\n- TB_SMRM1011"; r4_2.condition = "COUNT_CHECK"; r4_2.description = "불완전판매 SMS 발송 파일생성중 건";
        Rule r4_3 = new Rule(); r4_3.type = "SEARCH"; r4_3.target = "UMS 발송결과 -> 리턴코드 200"; r4_3.condition = "EQUALS_N"; r4_3.expectedCount = 1; r4_3.description = "UMS 발송결과 -> 리턴코드 200 1건";
        Rule r4_4 = new Rule(); r4_4.type = "DISPLAY"; r4_4.target = "불완전판매 SMS FP 발송 완료 건\n- TB_SMRM1011"; r4_4.condition = "COUNT_CHECK"; r4_4.description = "불완전판매 SMS FP 발송 완료 건";
        j4.rules.add(r4_1); j4.rules.add(r4_2); j4.rules.add(r4_3); j4.rules.add(r4_4);
        list.add(j4);

        // 5. smrmJob103
        JobPolicy j5 = new JobPolicy();
        j5.jobNo = "05"; j5.jobName = "smrmJob103"; j5.jobTitle = "[전일] smrmJob103 불판 확인 SMS 발송(고객?)"; j5.filePrefix = "05_smrmJob103_"; j5.rawPattern = "_11402_";
        j5.holidayPattern = "비영업일에는 해당 JOB이 (수행|수혈)되지 않습니다.";
        Rule r5_1 = new Rule(); r5_1.type = "SEARCH"; r5_1.target = "UMS 발송결과 -> 리턴코드 200"; r5_1.condition = "EQUALS_N"; r5_1.expectedCount = 1; r5_1.description = "UMS 발송결과 -> 리턴코드 200 1건";
        Rule r5_2 = new Rule(); r5_2.type = "DISPLAY"; r5_2.target = "불완전판매 SMS 고객 발송 Master[TB_SMRM1010] 의 대상 상태를 문자전송(01) 상태로 전환 완료 건\n- TB_SMRM1010"; r5_2.condition = "COUNT_CHECK"; r5_2.description = "불완전판매 SMS 고객 발송 Master 건";
        Rule r5_3 = new Rule(); r5_3.type = "DISPLAY"; r5_3.target = "불완전판매 SMS 고객 발송 완료 건\n- TB_SMRM1011"; r5_3.condition = "COUNT_CHECK"; r5_3.description = "불완전판매 SMS 고객 발송 완료 건";
        j5.rules.add(r5_1); j5.rules.add(r5_2); j5.rules.add(r5_3);
        list.add(j5);

        // 6. smrmJob104
        JobPolicy j6 = new JobPolicy();
        j6.jobNo = "06"; j6.jobName = "smrmJob104"; j6.jobTitle = "smrmJob104 생성파일정보 등록*"; j6.filePrefix = "06_smrmJob104_"; j6.rawPattern = "_11403_";
        Rule r6 = new Rule(); r6.type = "STEP_METRICS"; r6.stepName = "smrmJob104001"; r6.condition = "ROLLBACK_ZERO"; r6.description = "StepName : smrmJob104001 (Rollback 0건)";
        j6.rules.add(r6);
        list.add(j6);

        // 7. smpmJob203
        JobPolicy j7 = new JobPolicy();
        j7.jobNo = "07"; j7.jobName = "smpmJob203"; j7.jobTitle = "smpmJob203 상품목록적재_한생(파일>DB)"; j7.filePrefix = "07_smpmJob203_"; j7.rawPattern = "_11259_";
        Rule r7_1 = new Rule(); r7_1.type = "SEARCH"; r7_1.target = "updateHliProduct"; r7_1.condition = "COUNT_CHECK"; r7_1.description = "수정[updateHliProduct] 검색 건수확인";
        Rule r7_2 = new Rule(); r7_2.type = "SEARCH"; r7_2.target = "insertHliProductList"; r7_2.condition = "COUNT_CHECK"; r7_2.description = "등록[insertHliProductList] 검색 건수확인";
        j7.rules.add(r7_1); j7.rules.add(r7_2);
        list.add(j7);

        // 8. smpmJob207
        JobPolicy j8 = new JobPolicy();
        j8.jobNo = "08"; j8.jobName = "smpmJob207"; j8.jobTitle = "smpmJob207 보험대리점협회 상품정보요청(수집:파일)"; j8.filePrefix = "08_smpmJob207_"; j8.rawPattern = "_11262_";
        Rule r8_1 = new Rule(); r8_1.type = "SEARCH"; r8_1.target = "HTTP/1.1 200"; r8_1.condition = "COUNT_CHECK"; r8_1.description = "HTTP/1.1 200 검색 건수확인";
        Rule r8_2 = new Rule(); r8_2.type = "DISPLAY"; r8_2.target = "totalCount"; r8_2.condition = "COUNT_CHECK"; r8_2.description = "totalCount 건수확인";
        Rule r8_3 = new Rule(); r8_3.type = "DISPLAY"; r8_3.target = "totalPage"; r8_3.condition = "COUNT_CHECK"; r8_3.description = "totalPage 건수확인";
        j8.rules.add(r8_1); j8.rules.add(r8_2); j8.rules.add(r8_3);
        list.add(j8);

        // 9. smpmJob208
        JobPolicy j9 = new JobPolicy();
        j9.jobNo = "09"; j9.jobName = "smpmJob208"; j9.jobTitle = "smpmJob208 보험대리점협회 상품 적재 (JSON 파일>DB)"; j9.filePrefix = "09_smpmJob208_"; j9.rawPattern = "_11263_";
        Rule r9_1 = new Rule(); r9_1.type = "STEP_METRICS"; r9_1.stepName = "smpmJob208001"; r9_1.condition = "ROLLBACK_ZERO"; r9_1.description = "StepName : smpmJob208001 (Rollback 0건)";
        Rule r9_2 = new Rule(); r9_2.type = "DISPLAY"; r9_2.target = "SmpmSkipPolicy"; r9_2.condition = "ERROR_IF_PRESENT"; r9_2.description = "SmpmSkipPolicy 표시 (0건)";
        j9.rules.add(r9_1); j9.rules.add(r9_2);
        list.add(j9);

        // 10. smpmJob211
        JobPolicy j10 = new JobPolicy();
        j10.jobNo = "10"; j10.jobName = "smpmJob211"; j10.jobTitle = "smpmJob211 상비서 징구현황 메일발송"; j10.filePrefix = "10_smpmJob211_"; j10.rawPattern = "_11266_";
        Rule r10 = new Rule(); r10.type = "SEARCH"; r10.target = "Emali 발송결과 -> 리턴코드 200"; r10.regex = "Ema(i|li) 발송결과 -> 리턴코드 200"; r10.condition = "EQUALS_N"; r10.expectedCount = 3; r10.description = "Email 발송결과 200 3건";
        j10.rules.add(r10);
        list.add(j10);

        // 11. smpmJob212_1
        JobPolicy j11 = new JobPolicy();
        j11.jobNo = "11"; j11.jobName = "smpmJob212_1"; j11.jobTitle = "smpmJob212_1 확인서 통합적재 - 한생 건수 로그확인"; j11.filePrefix = "11_smpmJob212_1_"; j11.rawPattern = "_11268_%16_1";
        Rule r11 = new Rule(); r11.type = "SEARCH"; r11.target = "파일 생성 완료"; r11.condition = "COUNT_CHECK"; r11.description = "한생 [파일 생성 완료] 검색 건수확인";
        j11.rules.add(r11);
        list.add(j11);

        // 12. smpmJob212_2
        JobPolicy j12 = new JobPolicy();
        j12.jobNo = "12"; j12.jobName = "smpmJob212_2"; j12.jobTitle = "smpmJob212_2 확인서 통합적재 - 한손 건수 로그확인"; j12.filePrefix = "12_smpmJob212_2_"; j12.rawPattern = "_11268_%18_1";
        Rule r12 = new Rule(); r12.type = "SEARCH"; r12.target = "파일 생성 완료"; r12.condition = "COUNT_CHECK"; r12.description = "한손 [파일 생성 완료] 검색 건수확인";
        j12.rules.add(r12);
        list.add(j12);

        // 13. smpmJob213
        JobPolicy j13 = new JobPolicy();
        j13.jobNo = "13"; j13.jobName = "smpmJob213"; j13.jobTitle = "[전일] smpmJob213 전일자 실행확인"; j13.filePrefix = "13_smpmJob213_"; j13.rawPattern = "_12833_";
        Rule r13_1 = new Rule(); r13_1.type = "DISPLAY"; r13_1.target = "비정상 종료 삭제 건수"; r13_1.condition = "COUNT_CHECK"; r13_1.description = "비정상 종료 삭제 건수";
        Rule r13_2 = new Rule(); r13_2.type = "DISPLAY"; r13_2.target = "상품비교설명확인서 미징구 건수"; r13_2.condition = "COUNT_CHECK"; r13_2.description = "상품비교설명확인서 미징구 건수";
        Rule r13_3 = new Rule(); r13_3.type = "DISPLAY"; r13_3.target = "상품비교설명확인서 첨부여부 상태변경 건수"; r13_3.condition = "COUNT_CHECK"; r13_3.description = "상품비교설명확인서 첨부여부 상태변경 건수";
        Rule r13_4 = new Rule(); r13_4.type = "DISPLAY"; r13_4.target = "상품비교설명확인서 미징구 알림발송 대상"; r13_4.condition = "COUNT_CHECK"; r13_4.description = "상품비교설명확인서 미징구 알림발송 대상";
        Rule r13_5 = new Rule(); r13_5.type = "DISPLAY"; r13_5.target = "상품비교설명확인서 발송실패건 업데이트"; r13_5.condition = "COUNT_CHECK"; r13_5.description = "상품비교설명확인서 발송실패건 업데이트";
        Rule r13_6 = new Rule(); r13_6.type = "DISPLAY"; r13_6.target = "상품비교설명확인서 발송성공건 info"; r13_6.regex = "상품비교설명확인서 발송성공건 info(\\s*업데이트)?"; r13_6.condition = "COUNT_CHECK"; r13_6.description = "상품비교설명확인서 발송성공건 info";
        Rule r13_7 = new Rule(); r13_7.type = "DISPLAY"; r13_7.target = "상품비교설명확인서 발송성공건 일련번호 부여"; r13_7.condition = "COUNT_CHECK"; r13_7.description = "상품비교설명확인서 발송성공건 일련번호 부여";
        j13.rules.add(r13_1); j13.rules.add(r13_2); j13.rules.add(r13_3); j13.rules.add(r13_4); j13.rules.add(r13_5); j13.rules.add(r13_6); j13.rules.add(r13_7);
        list.add(j13);

        // 14. smpmJob220
        JobPolicy j14 = new JobPolicy();
        j14.jobNo = "14"; j14.jobName = "smpmJob220"; j14.jobTitle = "smpmJob220 비교상품목록수신(내재화): 로그확인"; j14.filePrefix = "14_smpmJob220_"; j14.rawPattern = "_12871_";
        Rule r14 = new Rule(); r14.type = "DISPLAY"; r14.target = "STEP2-3. 기등록 상품추천 목록 등록하기"; r14.condition = "COUNT_CHECK"; r14.description = "STEP2-3. 기등록 상품추천 목록 등록하기";
        j14.rules.add(r14);
        list.add(j14);

        // 15. smpcJob002
        JobPolicy j15 = new JobPolicy();
        j15.jobNo = "15"; j15.jobName = "smpcJob002"; j15.jobTitle = "smpcJob002 파기"; j15.filePrefix = "15_smpcJob002_"; j15.rawPattern = "_12928_";
        Rule r15_1 = new Rule(); r15_1.type = "DISPLAY"; r15_1.target = "00.상품비교설명확인서 관리 파기목록 조회"; r15_1.condition = "COUNT_CHECK"; r15_1.description = "00.상품비교설명확인서 관리 파기목록 조회";
        Rule r15_2 = new Rule(); r15_2.type = "SEARCH"; r15_2.target = "pcicSttsCode=10"; r15_2.condition = "COUNT_CHECK"; r15_2.description = "pcicSttsCode=10 검색 건수확인";
        Rule r15_3 = new Rule(); r15_3.type = "SEARCH"; r15_3.target = "pcicSttsCode=20"; r15_3.condition = "COUNT_CHECK"; r15_3.description = "pcicSttsCode=20 검색 건수확인";
        Rule r15_4 = new Rule(); r15_4.type = "SEARCH"; r15_4.target = "pcicSttsCode=30"; r15_4.condition = "COUNT_CHECK"; r15_4.description = "pcicSttsCode=30 검색 건수확인";
        j15.rules.add(r15_1); j15.rules.add(r15_2); j15.rules.add(r15_3); j15.rules.add(r15_4);
        list.add(j15);

        // 16. smpcJob003
        JobPolicy j16 = new JobPolicy();
        j16.jobNo = "16"; j16.jobName = "smpcJob003"; j16.jobTitle = "smpcJob003 협회상품코드설정: 로그확인"; j16.filePrefix = "16_smpcJob003_"; j16.rawPattern = "_13104_";
        Rule r16_1 = new Rule(); r16_1.type = "DISPLAY"; r16_1.target = "1. 협회상품기준 미등록 상품 건수="; r16_1.condition = "COUNT_CHECK"; r16_1.description = "1. 협회상품기준 미등록 상품 건수=";
        Rule r16_2 = new Rule(); r16_2.type = "DISPLAY"; r16_2.target = "2. 제휴사상품코드 중복 등록 협회상품 건수="; r16_2.condition = "COUNT_CHECK"; r16_2.description = "2. 제휴사상품코드 중복 등록 협회상품 건수=";
        Rule r16_3 = new Rule(); r16_3.type = "DISPLAY"; r16_3.target = "3. [협회상품코드 현행화 대상 불일치 상품 건수]="; r16_3.regex = "3\\.\\s*\\[?협회상품코드 현행화 대상 불일치 상품 건수\\]?="; r16_3.condition = "COUNT_CHECK"; r16_3.description = "3. [협회상품코드 현행화 대상 불일치 상품 건수]=";
        Rule r16_4 = new Rule(); r16_4.type = "DISPLAY"; r16_4.target = "6. [협회상품코드 현행화 건수]="; r16_4.regex = "6\\.\\s*\\[?협회상품코드 현행화 건수\\]?="; r16_4.condition = "COUNT_CHECK"; r16_4.description = "6. [협회상품코드 현행화 건수]=";
        j16.rules.add(r16_1); j16.rules.add(r16_2); j16.rules.add(r16_3); j16.rules.add(r16_4);
        list.add(j16);

        // 17. SmpcJob001
        JobPolicy j17 = new JobPolicy();
        j17.jobNo = "17"; j17.jobName = "SmpcJob001"; j17.jobTitle = "SmpcJob001 징구요청"; j17.filePrefix = "17_smpcJob001_"; j17.rawPattern = "_12926_";
        Rule r17_1 = new Rule(); r17_1.type = "DISPLAY"; r17_1.target = "STEP_01.징구요청대상 확인서정보"; r17_1.condition = "COUNT_CHECK"; r17_1.description = "STEP_01.징구요청대상 확인서정보";
        Rule r17_2 = new Rule(); r17_2.type = "SEARCH"; r17_2.target = "piciBlngCnt : 0"; r17_2.condition = "COUNT_CHECK"; r17_2.description = "piciBlngCnt : 0 검색 건수확인";
        Rule r17_3 = new Rule(); r17_3.type = "SEARCH"; r17_3.target = "piciBlngCnt : 1"; r17_3.condition = "COUNT_CHECK"; r17_3.description = "piciBlngCnt : 1 검색 건수확인";
        j17.rules.add(r17_1); j17.rules.add(r17_2); j17.rules.add(r17_3);
        list.add(j17);

        return list;
    }
}
