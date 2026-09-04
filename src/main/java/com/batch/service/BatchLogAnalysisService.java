package com.batch.service;

import com.batch.analyzer.JobAnalysisContext;
import com.batch.analyzer.LogAnalyzer;
import com.batch.config.BatchConfig;
import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.LogConstants;
import com.batch.model.RuleResult;
import com.batch.policy.PolicyManager;
import com.batch.report.ReportGenerator;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * =====================================================================================
 * [비즈니스 서비스 및 오케스트레이터 (Service Layer): BatchLogAnalysisService]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 리팩토링 포인트:
 * 1. 단일 책임 원칙 (SRP) 극대화:
 *    - 작업 폴더 결정 로직은 WorkFolderResolver 로 위임
 *    - 원본 파일명 표준화(--rename) 로직은 LogFileRenamer 로 위임
 *    - 본 서비스는 비즈니스 흐름(정책 로드 -> 폴더 해석 -> 분석 실행 -> 리포트 생성) 오케스트레이션에만 집중합니다.
 * 2. 생성자 기반 의존성 주입 (DI) & 확장성:
 *    - PolicyManager, LogAnalyzer, WorkFolderResolver, LogFileRenamer를 주입받아
 *      단위 테스트 시 가짜(Mock) 객체로 손쉽게 격리 테스트가 가능합니다.
 * 3. 100% 하위 호환성 유지:
 *    - 기존 AnalysisSummary 내부 클래스 및 정적 헬퍼 메서드(resolveWorkFolder 등) 시그니처를 온전히 보존합니다.
 * =====================================================================================
 */
@Service
public class BatchLogAnalysisService {

    private final PolicyManager policyManager;
    private final LogAnalyzer logAnalyzer;
    private final WorkFolderResolver folderResolver;
    private final LogFileRenamer fileRenamer;

    // 싱글톤 기본 헬퍼 인스턴스 (정적 위임용)
    private static final WorkFolderResolver DEFAULT_FOLDER_RESOLVER = new WorkFolderResolver();
    private static final LogFileRenamer DEFAULT_FILE_RENAMER = new LogFileRenamer();

    public BatchLogAnalysisService() {
        this(new PolicyManager(), new LogAnalyzer(), new WorkFolderResolver(), new LogFileRenamer());
    }

    public BatchLogAnalysisService(PolicyManager policyManager) {
        this(policyManager, new LogAnalyzer(), new WorkFolderResolver(), new LogFileRenamer());
    }

    public BatchLogAnalysisService(PolicyManager policyManager, LogAnalyzer logAnalyzer) {
        this(policyManager, logAnalyzer, new WorkFolderResolver(), new LogFileRenamer());
    }

    public BatchLogAnalysisService(PolicyManager policyManager,
                                  LogAnalyzer logAnalyzer,
                                  WorkFolderResolver folderResolver,
                                  LogFileRenamer fileRenamer) {
        this.policyManager = policyManager != null ? policyManager : new PolicyManager();
        this.logAnalyzer = logAnalyzer != null ? logAnalyzer : new LogAnalyzer();
        this.folderResolver = folderResolver != null ? folderResolver : new WorkFolderResolver();
        this.fileRenamer = fileRenamer != null ? fileRenamer : new LogFileRenamer();
    }

    /**
     * 분석 실행 결과 요약 DTO (com.batch.model.AnalysisSummary 상속으로 100% 하위 호환)
     */
    public static class AnalysisSummary extends com.batch.model.AnalysisSummary {
        public AnalysisSummary() {
            super();
        }

        public AnalysisSummary(com.batch.model.AnalysisSummary other) {
            super(other);
        }
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
        String baseFolder = BatchConfig.getBaseFolder();

        // 1. 정책 메타데이터 로드
        policyManager.loadPolicies();
        List<JobPolicy> policies = policyManager.getPolicies();
        summary.totalJobs = policies.size();

        // 2. 대상 폴더 탐색 및 결정 (WorkFolderResolver 위임)
        File resolvedFolder = folderResolver.resolve(logFileSrc, baseFolder);

        if (resolvedFolder != null && resolvedFolder.exists() && folderResolver.hasLogFiles(resolvedFolder)) {
            // [정상 분석 케이스] 대상 폴더에 로그 파일이 존재하는 경우
            summary.workFolder = resolvedFolder;
            summary.folderName = resolvedFolder.getName();
            summary.success = true;

            System.out.println(">> 분석 대상 폴더: " + resolvedFolder.getAbsolutePath() + " (" + summary.folderName + ")");
            System.out.println(">> 로드된 배치 정책 수: " + policies.size() + "개 JOB");
            if (skipDateCheck) {
                System.out.println(">> [옵션 적용] 일자 검증 건너뛰기(--skipDateCheck) 활성화: 폴더 내 모든 로그를 시간 무관 처리합니다.");
            }

            // 자동 파일명 변경 요청 시 실행 (LogFileRenamer 위임)
            if (autoRename) {
                int renamed = fileRenamer.rename(resolvedFolder, policies);
                if (renamed > 0) {
                    System.out.println(">> 총 " + renamed + "개 원본 로그 파일명이 표준 접두사로 변경되었습니다.");
                }
            }

            File[] logFiles = resolvedFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
            if (logFiles == null) logFiles = new File[0];

            for (JobPolicy policy : policies) {
                JobAnalysisContext context = JobAnalysisContext.builder()
                        .workFolder(resolvedFolder)
                        .logFiles(logFiles)
                        .policy(policy)
                        .folderName(summary.folderName)
                        .skipDateCheck(skipDateCheck)
                        .build();

                CheckResult cr = logAnalyzer.checkJobInstance(context);
                summary.addResult(cr);
            }
        } else {
            // [조건 3: 분석 실패 케이스] 날짜 폴더가 없거나 최종 폴더에 로그 파일이 없는 경우
            summary.workFolder = resolvedFolder;
            summary.folderName = folderResolver.determineFolderName(resolvedFolder, logFileSrc, baseFolder);
            summary.success = false;

            System.out.println(">> [알림] 유효한 로그 파일이 존재하지 않습니다: " + (resolvedFolder != null ? resolvedFolder.getAbsolutePath() : logFileSrc));
            System.out.println(">> [조건 3] 분석 실패 결과 리포트(FAIL)를 자동 생성합니다. 대상 폴더명: " + summary.folderName);

            for (JobPolicy policy : policies) {
                CheckResult cr = new CheckResult(policy);
                cr.markAsFileNotFound((policy != null ? policy.filePrefix : "") + "*.log (미발견)");
                cr.addRuleResult(RuleResult.fail(LogConstants.RULE_NO_ERROR, "로그 파일 존재 여부", 
                        "", "미발견", LogConstants.MSG_FILE_NOT_FOUND));

                summary.addResult(cr);
            }
        }

        // 3. 콘솔 결과 출력
        ReportGenerator.printConsoleReport(summary);

        // 4. 마크다운 리포트 파일 생성 (실행 시마다 기존 파일 삭제 후 재생성)
        summary.reportFile = ReportGenerator.saveMarkdownReport(summary);

        return summary;
    }

    // =========================================================================
    // 정적 헬퍼 메서드 (100% 하위 호환성 보장)
    // =========================================================================

    public static File resolveWorkFolder(String logFileSrc, String baseFolder) {
        return DEFAULT_FOLDER_RESOLVER.resolve(logFileSrc, baseFolder);
    }

    public static boolean hasLogFiles(File dir) {
        return DEFAULT_FOLDER_RESOLVER.hasLogFiles(dir);
    }

    public static File getLatestDateFolder(File parent) {
        return DEFAULT_FOLDER_RESOLVER.getLatestDateFolder(parent);
    }

    public static int renameLogFiles(File workFolder, List<JobPolicy> policies) {
        return DEFAULT_FILE_RENAMER.rename(workFolder, policies);
    }
}
