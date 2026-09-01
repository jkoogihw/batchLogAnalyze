package com.batch;

import com.batch.config.Config;
import com.batch.policy.PolicyManager;
import com.batch.analyzer.LogAnalyzer;
import com.batch.report.ReportGenerator;
import com.batch.model.*;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.util.*;

/**
 * 배치로그 분석 및 정상 여부 검증 프로그램
 * 
 * 실행 파라미터(logFileSrc) 지원:
 * 1. logFileSrc 값 지정 시: 해당 폴더에 .log 파일이 존재하면 직접 대상 폴더로 설정하여 분석
 * 2. logFileSrc 폴더 내에 .log 파일이 없으면: 하위의 6자리 날짜 포맷(\\d{6}, 예: 260901) 중 최신 폴더를 찾아 대상 폴더로 설정
 * 3. 날짜 포맷 폴더가 없거나 최종 폴더에 로그 파일이 없으면: 분석 실패(FAIL) 결과 리포트 파일 자동 생성
 * 4. logFileSrc 값으로 6자리 날짜 포맷(\\d{6})이 지정되면: 기본 설정 경로(base.folder)의 하위 폴더를 대상으로 분석
 * 5. 파라미터 미지정 시: 기본 설정 경로(base.folder) 내 최신 날짜 포맷(\\d{6}) 폴더 자동 탐색
 */
@SpringBootApplication
public class CheckLog {

    /**
     * 분석 실행 결과 요약 모델
     */
    public static class AnalysisSummary {
        public File workFolder;
        public String folderName;
        public List<CheckResult> results = new ArrayList<>();
        public int totalJobs;
        public int passCount;
        public int failCount;
        public boolean success; // 로그 파일이 존재하여 정상 분석 완료 시 true, 대상 파일 미존재로 실패 리포트 생성 시 false
        public File reportFile;
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  [배치 로그 자동 분석 및 정상 여부 검증 프로그램 (CheckLog)]");
        System.out.println("================================================================================");

        try {
            AnalysisSummary summary = runAnalysis(args);
            if (!summary.success) {
                System.err.println("[오류] 대상 폴더에 분석 가능한 로그 파일이 존재하지 않아 분석실패 리포트를 생성했습니다: " + summary.folderName);
            }
        } catch (RuntimeException e) {
            System.err.println("[오류] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 파라미터 기반 분석 실행 (테스트 및 외부 호출 가능)
     */
    public static AnalysisSummary runAnalysis(String[] args) {
        String logFileSrc = extractLogFileSrc(args);
        boolean autoRename = hasRenameOption(args);
        return runAnalysis(logFileSrc, autoRename);
    }

    /**
     * logFileSrc 경로/날짜 기반 분석 실행 (기본 rename 안함)
     */
    public static AnalysisSummary runAnalysis(String logFileSrc) {
        return runAnalysis(logFileSrc, false);
    }

    /**
     * logFileSrc 경로/날짜 및 자동 파일명 변경 옵션 기반 분석 실행
     */
    public static AnalysisSummary runAnalysis(String logFileSrc, boolean autoRename) {
        AnalysisSummary summary = new AnalysisSummary();
        String baseFolder = Config.get("base.folder", ".");

        // 1. 정책 메타데이터 로드
        PolicyManager policyManager = new PolicyManager();
        policyManager.loadPolicies();
        List<JobPolicy> policies = policyManager.getPolicies();
        summary.totalJobs = policies.size();

        // 2. 대상 폴더 탐색 및 결정 (조건 1, 2, 3, 4)
        File resolvedFolder = resolveWorkFolder(logFileSrc, baseFolder);

        if (resolvedFolder != null && resolvedFolder.exists() && hasLogFiles(resolvedFolder)) {
            // [정상 분석 케이스] 대상 폴더에 로그 파일이 존재하는 경우
            summary.workFolder = resolvedFolder;
            summary.folderName = resolvedFolder.getName();
            summary.success = true;

            System.out.println(">> 분석 대상 폴더: " + resolvedFolder.getAbsolutePath() + " (" + summary.folderName + ")");
            System.out.println(">> 로드된 배치 정책 수: " + policies.size() + "개 JOB");

            // 자동 파일명 변경 요청 시 실행
            if (autoRename) {
                int renamed = renameLogFiles(resolvedFolder, policies);
                if (renamed > 0) {
                    System.out.println(">> 총 " + renamed + "개 원본 로그 파일명이 표준 접두사로 변경되었습니다.");
                }
            }

            File[] logFiles = resolvedFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
            if (logFiles == null) logFiles = new File[0];

            for (JobPolicy policy : policies) {
                CheckResult cr = LogAnalyzer.checkJob(resolvedFolder, logFiles, policy);
                summary.results.add(cr);
                if (cr.overallPassed) {
                    summary.passCount++;
                } else {
                    summary.failCount++;
                }
            }
        } else {
            // [조건 3: 분석 실패 케이스] 날짜 폴더가 없거나 최종 폴더에 로그 파일이 없는 경우
            summary.workFolder = resolvedFolder;
            summary.folderName = determineFolderName(resolvedFolder, logFileSrc, baseFolder);
            summary.success = false;
            summary.passCount = 0;
            summary.failCount = policies.size();

            System.out.println(">> [알림] 유효한 로그 파일이 존재하지 않습니다: " + (resolvedFolder != null ? resolvedFolder.getAbsolutePath() : logFileSrc));
            System.out.println(">> [조건 3] 분석 실패 결과 리포트(FAIL)를 자동 생성합니다. 대상 폴더명: " + summary.folderName);

            for (JobPolicy policy : policies) {
                CheckResult cr = new CheckResult(policy.jobNo, policy.jobName, policy.jobTitle);
                cr.fileFound = false;
                cr.fileName = policy.filePrefix + "*.log (미발견)";
                cr.overallPassed = false;

                RuleResult rr = new RuleResult();
                rr.description = "로그 파일 존재 여부";
                rr.passed = false;
                rr.message = "해당 JOB의 로그 파일이 존재하지 않습니다.";
                cr.addRuleResult(rr);

                summary.results.add(cr);
            }
        }

        // 3. 콘솔 결과 출력
        ReportGenerator.printConsoleReport(summary.folderName, summary.results, summary.totalJobs, summary.passCount, summary.failCount);

        // 4. 마크다운 리포트 파일 생성 (실행 시마다 기존 파일 삭제 후 재생성)
        summary.reportFile = ReportGenerator.saveMarkdownReport(summary.folderName, summary.results, summary.totalJobs, summary.passCount, summary.failCount);

        return summary;
    }

    /**
     * 실행 파라미터에서 logFileSrc 값 추출
     * - --logFileSrc=path, -logFileSrc=path, logFileSrc=path 지원
     * - --logFileSrc path, -logFileSrc path, logFileSrc path 지원
     * - 첫 번째 위치 인자(positional arg) 지원
     */
    public static String extractLogFileSrc(String[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("--logFileSrc=")) {
                return arg.substring("--logFileSrc=".length()).trim();
            } else if (arg.startsWith("-logFileSrc=")) {
                return arg.substring("-logFileSrc=".length()).trim();
            } else if (arg.startsWith("logFileSrc=")) {
                return arg.substring("logFileSrc=".length()).trim();
            } else if (arg.equalsIgnoreCase("--logFileSrc") || arg.equalsIgnoreCase("-logFileSrc") || arg.equalsIgnoreCase("logFileSrc")) {
                if (i + 1 < args.length) {
                    return args[i + 1].trim();
                }
            }
        }
        // 위치 기반 인자 중 옵션 플래그가 아닌 첫 번째 인자 반환
        for (String arg : args) {
            String trimmed = arg.trim();
            if (!trimmed.startsWith("-") && !trimmed.equalsIgnoreCase("rename")) {
                return trimmed;
            }
        }
        return "";
    }

    /**
     * --rename 또는 -rename 옵션 포함 여부 확인
     */
    public static boolean hasRenameOption(String[] args) {
        if (args == null) return false;
        for (String a : args) {
            String t = a.trim();
            if (t.equalsIgnoreCase("--rename") || t.equalsIgnoreCase("-rename") || t.equalsIgnoreCase("rename")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 원본 파일명 패턴(rawPattern)으로 매칭된 로그 파일들을 표준 접두사(filePrefix) 파일명으로 일괄 변경
     */
    public static int renameLogFiles(File workFolder, List<JobPolicy> policies) {
        if (workFolder == null || !workFolder.exists() || !workFolder.isDirectory()) return 0;
        File[] files = workFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
        if (files == null || files.length == 0) return 0;

        int renamedCount = 0;
        for (JobPolicy policy : policies) {
            File target = LogAnalyzer.findTargetFile(files, policy);
            if (target != null && !target.getName().startsWith(policy.filePrefix)) {
                File renamedFile = new File(workFolder, policy.filePrefix + target.getName());
                if (target.renameTo(renamedFile)) {
                    System.out.println(">> [파일명 변경] " + target.getName() + " -> " + renamedFile.getName());
                    renamedCount++;
                }
            }
        }
        return renamedCount;
    }

    /**
     * 대상 로그 폴더 결정 로직
     */
    public static File resolveWorkFolder(String logFileSrc, String baseFolder) {
        // 4. logFileSrc 값으로 6자리 날짜 포맷(\\d{6})이 설정된 경우: 기본경로의 하위폴더 대상
        if (logFileSrc != null && logFileSrc.matches("\\d{6}")) {
            File dateFolder = new File(baseFolder, logFileSrc);
            if (dateFolder.exists() && hasLogFiles(dateFolder)) {
                return dateFolder;
            }
            return dateFolder.exists() ? dateFolder : null;
        }

        // 파라미터가 지정된 경우
        if (logFileSrc != null && !logFileSrc.isEmpty()) {
            File targetDir = new File(logFileSrc);
            if (!targetDir.isAbsolute() && !targetDir.exists()) {
                File underBase = new File(baseFolder, logFileSrc);
                if (underBase.exists()) {
                    targetDir = underBase;
                }
            }

            if (targetDir.exists() && targetDir.isDirectory()) {
                // 1. logFileSrc 해당 위치 폴더에 로그 파일이 존재하는 경우
                if (hasLogFiles(targetDir)) {
                    return targetDir;
                }

                // 2. logFileSrc 폴더에 로그 파일이 없는 경우: 내부 6자리 날짜 포맷(\\d{6}) 중 최신 폴더 탐색
                File latestDateFolder = getLatestDateFolder(targetDir);
                if (latestDateFolder != null && hasLogFiles(latestDateFolder)) {
                    return latestDateFolder;
                }

                // 최신 날짜 폴더가 존재는 하나 로그 파일이 없는 경우 해당 폴더 반환 (실패 리포트용)
                if (latestDateFolder != null) {
                    return latestDateFolder;
                }

                return targetDir;
            }
            return targetDir;
        }

        // 파라미터 미지정 시: 기본경로(base.folder)의 최신 날짜 폴더 탐색
        File baseDir = new File(baseFolder);
        if (baseDir.exists() && baseDir.isDirectory()) {
            if (hasLogFiles(baseDir)) {
                return baseDir;
            }
            File latestBaseDateFolder = getLatestDateFolder(baseDir);
            if (latestBaseDateFolder != null) {
                return latestBaseDateFolder;
            }
        }

        return null;
    }

    /**
     * 폴더 내에 .log 파일이 1개 이상 존재하는지 확인
     */
    public static boolean hasLogFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        File[] logs = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".log"));
        return logs != null && logs.length > 0;
    }

    /**
     * 최신 날짜(6자리 숫자, \\d{6}) 폴더 조회
     */
    public static File getLatestDateFolder(File parent) {
        if (parent == null || !parent.exists() || !parent.isDirectory()) return null;
        File[] dirs = parent.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return null;

        return Arrays.stream(dirs)
                .filter(d -> d.getName().matches("\\d{6}"))
                .max(Comparator.comparingInt(d -> Integer.parseInt(d.getName())))
                .orElse(null);
    }

    /**
     * 문자열 경로 기반 최신 날짜 폴더 조회
     */
    public static File getLatestDateFolder(String parentPath) {
        if (parentPath == null || parentPath.isEmpty()) return null;
        return getLatestDateFolder(new File(parentPath));
    }

    /**
     * 리포트용 폴더명 결정 헬퍼
     */
    private static String determineFolderName(File folder, String logFileSrc, String baseFolder) {
        if (folder != null) {
            return folder.getName();
        }
        if (logFileSrc != null && !logFileSrc.isEmpty()) {
            File f = new File(logFileSrc);
            return f.getName().isEmpty() ? logFileSrc : f.getName();
        }
        File base = new File(baseFolder);
        return base.getName().isEmpty() ? "배치로그" : base.getName();
    }
}
