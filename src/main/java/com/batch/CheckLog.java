package com.batch;

import com.batch.model.JobPolicy;
import com.batch.service.BatchLogAnalysisService;
import com.batch.service.BatchLogAnalysisService.AnalysisSummary;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.util.List;

/**
 * =====================================================================================
 * [배치로그 분석 및 정상 여부 검증 프로그램 엔트리포인트 (CheckLog)]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 리팩토링 포인트:
 * 1. 단일 책임 원칙 (SRP):
 *    - 본 클래스는 CLI 파라미터 파싱 및 애플리케이션 부트스트랩/진입점 책임만을 담당하며,
 *      핵심 분석 오케스트레이션은 BatchLogAnalysisService에 위임합니다.
 * 2. 100% 하위 호환성 유지:
 *    - 기존 CLI 옵션, 정적 메서드(runAnalysis 등)를 그대로 유지하여 기존 테스트 및 스크립트와의 호환성을 보장합니다.
 * =====================================================================================
 */
@SpringBootApplication
public class CheckLog {

    // 하위 호환성을 위한 AnalysisSummary 별칭 타입 참조
    public static class AnalysisSummary extends BatchLogAnalysisService.AnalysisSummary {}

    private static final BatchLogAnalysisService service = new BatchLogAnalysisService();

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  [배치 로그 자동 분석 및 정상 여부 검증 프로그램 (CheckLog)]");
        System.out.println("================================================================================");

        try {
            CheckLog.AnalysisSummary summary = runAnalysis(args);
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
    public static CheckLog.AnalysisSummary runAnalysis(String[] args) {
        String logFileSrc = extractLogFileSrc(args);
        boolean autoRename = hasRenameOption(args);
        return runAnalysis(logFileSrc, autoRename);
    }

    /**
     * logFileSrc 경로/날짜 기반 분석 실행 (기본 rename 안함)
     */
    public static CheckLog.AnalysisSummary runAnalysis(String logFileSrc) {
        return runAnalysis(logFileSrc, false);
    }

    /**
     * logFileSrc 경로/날짜 및 자동 파일명 변경 옵션 기반 분석 실행
     */
    public static CheckLog.AnalysisSummary runAnalysis(String logFileSrc, boolean autoRename) {
        BatchLogAnalysisService.AnalysisSummary baseSummary = service.analyze(logFileSrc, autoRename);
        CheckLog.AnalysisSummary summary = new CheckLog.AnalysisSummary();
        summary.workFolder = baseSummary.workFolder;
        summary.folderName = baseSummary.folderName;
        summary.results = baseSummary.results;
        summary.totalJobs = baseSummary.totalJobs;
        summary.passCount = baseSummary.passCount;
        summary.failCount = baseSummary.failCount;
        summary.success = baseSummary.success;
        summary.reportFile = baseSummary.reportFile;
        return summary;
    }

    /**
     * 실행 파라미터에서 logFileSrc 값 추출
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
     * 대상 로그 폴더 결정 로직 (BatchLogAnalysisService에 위임)
     */
    public static File resolveWorkFolder(String logFileSrc, String baseFolder) {
        return BatchLogAnalysisService.resolveWorkFolder(logFileSrc, baseFolder);
    }

    /**
     * 폴더 내에 .log 파일이 1개 이상 존재하는지 확인 (BatchLogAnalysisService에 위임)
     */
    public static boolean hasLogFiles(File dir) {
        return BatchLogAnalysisService.hasLogFiles(dir);
    }

    /**
     * 최신 날짜 폴더 조회 (BatchLogAnalysisService에 위임)
     */
    public static File getLatestDateFolder(File parent) {
        return BatchLogAnalysisService.getLatestDateFolder(parent);
    }

    public static File getLatestDateFolder(String parentPath) {
        if (parentPath == null || parentPath.isEmpty()) return null;
        return getLatestDateFolder(new File(parentPath));
    }

    /**
     * 원본 파일명 패턴(rawPattern)으로 매칭된 로그 파일들을 표준 접두사(filePrefix) 파일명으로 일괄 변경
     */
    public static int renameLogFiles(File workFolder, List<JobPolicy> policies) {
        return BatchLogAnalysisService.renameLogFiles(workFolder, policies);
    }
}
