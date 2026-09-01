package com.batch;

import com.batch.config.Config;
import com.batch.policy.PolicyManager;
import com.batch.analyzer.LogAnalyzer;
import com.batch.report.ReportGenerator;
import com.batch.model.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 배치로그 분석 및 정상 여부 검증 프로그램
 * 
 * - 'policy_meta.json' 기반 정책 검증 (application.properties 설정)
 * - 검색(전체 발생 건수), 표시(키워드 뒤 건수 추출), 멀티라인 텍스트 매칭 지원
 * - 비영업일 예외 및 Spring Batch Step 요약 통계(RollbackCount) 검증
 * - 콘솔 출력 및 마크다운 리포트 자동 생성
 * 
 * 역할 분리 구조:
 * - Config: 설정 관리
 * - PolicyManager: 정책 로드/파싱
 * - LogAnalyzer: 로그 분석
 * - ReportGenerator: 리포트 생성
 */
public class CheckLog {

    
    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  [배치 로그 자동 분석 및 정상 여부 검증 프로그램 (CheckLog)]");
        System.out.println("================================================================================");

        try {
            // 1. 대상 폴더 결정
            String targetFolder = args.length > 0 ? args[0] : "";
            String baseFolder = Config.get("base.folder");
            File workFolder = targetFolder.isEmpty() ? 
                    getLatestDateFolder(baseFolder) : 
                    new File(baseFolder, targetFolder);

            if (workFolder == null || !workFolder.exists()) {
                System.err.println("[오류] 지정한 로그 대상 폴더가 존재하지 않습니다: " + workFolder);
                return;
            }

            String folderName = workFolder.getName();
            System.out.println(">> 분석 대상 폴더: " + workFolder.getAbsolutePath() + " (" + folderName + ")");

            // 2. 정책 메타데이터 로드
            PolicyManager policyManager = new PolicyManager();
            policyManager.loadPolicies();
            List<JobPolicy> policies = policyManager.getPolicies();
            System.out.println(">> 로드된 배치 정책 수: " + policies.size() + "개 JOB");

            // 3. 로그 파일 검색 및 검증 수행
            File[] logFiles = workFolder.listFiles((dir, name) -> name.endsWith(".log"));
            if (logFiles == null) logFiles = new File[0];

            List<CheckResult> results = new ArrayList<>();
            int passCount = 0;
            int failCount = 0;

            for (JobPolicy policy : policies) {
                CheckResult cr = LogAnalyzer.checkJob(workFolder, logFiles, policy);
                results.add(cr);
                if (cr.overallPassed) {
                    passCount++;
                } else {
                    failCount++;
                }
            }

            // 4. 콘솔 결과 출력
            int totalJobs = policies.size();
            ReportGenerator.printConsoleReport(folderName, results, totalJobs, passCount, failCount);

            // 5. 마크다운 리포트 파일 생성
            ReportGenerator.saveMarkdownReport(folderName, results, totalJobs, passCount, failCount);
            
        } catch (RuntimeException e) {
            System.err.println("[오류] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 최신 날짜 폴더 조회
     */
    private static File getLatestDateFolder(String parentPath) {
        File parent = new File(parentPath);
        File[] dirs = parent.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return null;

        return Arrays.stream(dirs)
                .filter(d -> d.getName().matches("\\d{6}"))
                .max(Comparator.comparingInt(d -> Integer.parseInt(d.getName())))
                .orElse(null);
    }
}
