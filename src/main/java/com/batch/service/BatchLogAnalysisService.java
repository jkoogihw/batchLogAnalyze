package com.batch.service;

import com.batch.analyzer.LogAnalyzer;
import com.batch.config.Config;
import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.RuleResult;
import com.batch.policy.PolicyManager;
import com.batch.report.ReportGenerator;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * =====================================================================================
 * [비즈니스 서비스 및 오케스트레이터 (Service Layer): BatchLogAnalysisService]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 및 스프링 아키텍처 원칙:
 * 1. 관심사의 분리 (Separation of Concerns):
 *    - CLI 입출력/진입점(CheckLog)과 실제 배치 로그 분석 비즈니스 흐름을 완벽히 분리합니다.
 * 2. 확장 가능한 스프링 빈 (@Service):
 *    - 향후 웹 API 컨트롤러(REST Controller), 스케줄러(Scheduler) 등 다양한 진입점에서
 *      해당 서비스를 의존성 주입(@Autowired / 생성자 주입)받아 즉시 재사용할 수 있습니다.
 * =====================================================================================
 */
@Service
public class BatchLogAnalysisService {

    private final PolicyManager policyManager;

    public BatchLogAnalysisService() {
        this(new PolicyManager());
    }

    public BatchLogAnalysisService(PolicyManager policyManager) {
        this.policyManager = policyManager != null ? policyManager : new PolicyManager();
    }

    /**
     * 분석 실행 결과 요약 DTO
     */
    public static class AnalysisSummary {
        public File workFolder;
        public String folderName;
        public List<CheckResult> results = new ArrayList<>();
        public int totalJobs;
        public int passCount;
        public int failCount;
        public boolean success; // 정상 로그 분석 성공 시 true, 대상 폴더/파일 부재로 실패 리포트 생성 시 false
        public File reportFile;
    }

    /**
     * 로그 폴더 경로 및 옵션 기반 배치 분석 실행
     *
     * @param logFileSrc 대상 폴더 경로 또는 6자리 날짜(\\d{6})
     * @param autoRename 원본 로그 파일명 표준화(--rename) 수행 여부
     * @return 분석 종합 결과 요약 (AnalysisSummary)
     */
    public AnalysisSummary analyze(String logFileSrc, boolean autoRename) {
        return analyze(logFileSrc, autoRename, false);
    }

    /**
     * 로그 폴더 경로 및 옵션 기반 배치 분석 실행 (일자 점검 스킵 옵션 지원)
     *
     * @param logFileSrc     대상 폴더 경로 또는 6자리 날짜(\\d{6})
     * @param autoRename     원본 로그 파일명 표준화(--rename) 수행 여부
     * @param skipDateCheck  일자 점검 건너뛰기 여부
     * @return 분석 종합 결과 요약 (AnalysisSummary)
     */
    public AnalysisSummary analyze(String logFileSrc, boolean autoRename, boolean skipDateCheck) {
        AnalysisSummary summary = new AnalysisSummary();
        String baseFolder = Config.get("base.folder", ".");

        // 1. 정책 메타데이터 로드
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
            if (skipDateCheck) {
                System.out.println(">> [옵션 적용] 일자 검증 건너뛰기(--skipDateCheck) 활성화: 폴더 내 모든 로그를 시간 무관 처리합니다.");
            }

            // 자동 파일명 변경 요청 시 실행 - 필수처리(미변경건만 처리됨)
            //if (autoRename) {
                int renamed = renameLogFiles(resolvedFolder, policies);
                if (renamed > 0) {
                    System.out.println(">> 총 " + renamed + "개 원본 로그 파일명이 표준 접두사로 변경되었습니다.");
                }
            //}

            File[] logFiles = resolvedFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
            if (logFiles == null) logFiles = new File[0];

            for (JobPolicy policy : policies) {
                CheckResult cr = LogAnalyzer.checkJob(resolvedFolder, logFiles, policy, summary.folderName, skipDateCheck);
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
                CheckResult cr = new CheckResult(policy);
                cr.fileFound = false;
                cr.fileName = policy.filePrefix + "*.log (미발견)";
                cr.overallPassed = false;

                RuleResult rr = new RuleResult();
                rr.ruleNo = "ERR";
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
     * 대상 로그 폴더 결정 엔진
     */
    public static File resolveWorkFolder(String logFileSrc, String baseFolder) {
        // 조건 4. logFileSrc 값으로 6자리 날짜 포맷(\\d{6})이 설정된 경우: 기본경로의 하위폴더 대상
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
                // 조건 1. logFileSrc 해당 위치 폴더에 로그 파일이 존재하는 경우
                if (hasLogFiles(targetDir)) {
                    return targetDir;
                }

                // 조건 2. logFileSrc 폴더에 로그 파일이 없는 경우: 내부 6자리 날짜 포맷(\\d{6}) 중 최신 폴더 탐색
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
