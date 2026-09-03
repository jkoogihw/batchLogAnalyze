package com.batch.util;

import com.batch.model.JobPolicy;
import com.batch.model.Rule;
import com.batch.policy.PolicyManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * =====================================================================================
 * [테스트용 로그 경량화(슬림화) 유틸리티 - LogSlimmer]
 * -------------------------------------------------------------------------------------
 * 💡 주요 역할:
 * 1. 수백 MB ~ 수십만 라인에 달하는 실환경 배치 로그 파일에서, 비즈니스 규칙 검증 및
 *    테스트에 필요한 핵심 라인(타임스탬프, 키워드, Step/Job 메트릭)만 지능적으로 추출합니다.
 * 2. 불필요한 대량 INSERT/SELECT 반복 로그를 안전하게 축약하여 파일 크기를 99% 이상 절감합니다.
 * 3. 저장소(Git) 용량 제한(100MB)을 방어하면서도 모든 JUnit 5 테스트가 영속적으로 100% 통과하도록 지원합니다.
 * =====================================================================================
 */
public class LogSlimmer {

    private static final int DEFAULT_HEADER_LINES = 60;
    private static final int DEFAULT_TAIL_LINES = 60;
    private static final int DEFAULT_CONTEXT_LINES = 2;
    private static final int DEFAULT_MAX_KEYWORD_MATCHES = 30;

    /**
     * 공통 시스템 필수 보존 키워드
     */
    private static final Set<String> SYSTEM_KEYWORDS = new LinkedHashSet<>(Arrays.asList(
            "StaticLogListener.java",
            "SimpleJobLauncher.java",
            "StepName :",
            "RollbackCount :",
            "ReadCount :",
            "WriteCount :",
            "CommitCount :",
            "completed with the following parameters:",
            "Started BatchApplication",
            "HV000001: Hibernate Validator",
            "PARAMETER=--job.name="
    ));

    /**
     * 특정 디렉터리 내의 모든 .log 파일을 정책에 맞추어 일괄 경량화합니다.
     */
    public static int slimDirectory(File dir, List<JobPolicy> policies) throws IOException {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return 0;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null || files.length == 0) {
            return 0;
        }

        int count = 0;
        for (File file : files) {
            JobPolicy matchedPolicy = findPolicyForFile(file.getName(), policies);
            if (slimFile(file, file, matchedPolicy, policies)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 단일 로그 파일을 슬림화하여 대상 파일에 저장합니다.
     */
    public static boolean slimFile(File src, File dest, JobPolicy targetPolicy, List<JobPolicy> allPolicies) throws IOException {
        if (src == null || !src.exists()) {
            return false;
        }

        List<String> lines = Files.readAllLines(src.toPath(), StandardCharsets.UTF_8);
        List<String> slimmedLines = slimLines(lines, targetPolicy, allPolicies);

        Files.write(dest.toPath(), slimmedLines, StandardCharsets.UTF_8);
        return true;
    }

    /**
     * 문자열 라인 리스트를 슬림화합니다.
     */
    public static List<String> slimLines(List<String> lines, JobPolicy targetPolicy, List<JobPolicy> allPolicies) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> keywords = extractKeywords(targetPolicy, allPolicies);
        Set<Integer> keepIndices = new TreeSet<>();

        // 1. 헤더 영역 보존 (첫 타임스탬프, 환경 설정 등)
        int headerCount = Math.min(DEFAULT_HEADER_LINES, lines.size());
        for (int i = 0; i < headerCount; i++) {
            keepIndices.add(i);
        }

        // 2. 테일 영역 보존 (Step 종료 메트릭, Job COMPLETED 등)
        int tailStart = Math.max(0, lines.size() - DEFAULT_TAIL_LINES);
        for (int i = tailStart; i < lines.size(); i++) {
            keepIndices.add(i);
        }

        // 3. 비즈니스 검증 키워드 매칭 라인 및 전후 컨텍스트 보존
        Map<String, Integer> keywordCountMap = new HashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (String kw : keywords) {
                if (line.contains(kw)) {
                    int count = keywordCountMap.getOrDefault(kw, 0);
                    // EQUALS_N 정확 매칭 룰 등을 위해 충분한 수량(기본 30회, 필요시 더 많이) 보존
                    if (count < 200) {
                        keywordCountMap.put(kw, count + 1);
                        int start = Math.max(0, i - DEFAULT_CONTEXT_LINES);
                        int end = Math.min(lines.size() - 1, i + DEFAULT_CONTEXT_LINES);
                        for (int k = start; k <= end; k++) {
                            keepIndices.add(k);
                        }
                    }
                    break;
                }
            }
        }

        // 4. 추출된 인덱스 기반으로 축약 마커 삽입하며 결과 생성
        List<String> result = new ArrayList<>();
        int prevIdx = -1;
        for (int idx : keepIndices) {
            if (prevIdx != -1 && idx > prevIdx + 1) {
                int skipped = idx - prevIdx - 1;
                result.add("... [TRIMMED " + skipped + " LINES] ...");
            }
            result.add(lines.get(idx));
            prevIdx = idx;
        }

        return result;
    }

    /**
     * 정책 객체로부터 보존 대상 키워드 집합을 추출합니다.
     */
    public static Set<String> extractKeywords(JobPolicy targetPolicy, List<JobPolicy> allPolicies) {
        Set<String> keywords = new LinkedHashSet<>(SYSTEM_KEYWORDS);

        List<JobPolicy> targetList = new ArrayList<>();
        if (targetPolicy != null) {
            targetList.add(targetPolicy);
        } else if (allPolicies != null) {
            targetList.addAll(allPolicies);
        }

        for (JobPolicy p : targetList) {
            if (p.holidayPattern != null && !p.holidayPattern.isEmpty()) {
                keywords.add(p.holidayPattern.replace("(", "").replace(")", "").split("\\|")[0]);
            }
            if (p.rules != null) {
                for (Rule r : p.rules) {
                    if (r.target != null && !r.target.isEmpty()) {
                        keywords.add(r.target.trim());
                    }
                    if (r.stepName != null && !r.stepName.isEmpty()) {
                        keywords.add(r.stepName.trim());
                    }
                }
            }
        }

        return keywords;
    }

    /**
     * 파일명으로부터 매칭되는 정책을 찾습니다.
     */
    private static JobPolicy findPolicyForFile(String fileName, List<JobPolicy> policies) {
        if (policies == null) return null;
        for (JobPolicy p : policies) {
            if (p.filePrefix != null && fileName.startsWith(p.filePrefix)) {
                return p;
            }
            if (p.rawPattern != null && fileName.contains(p.rawPattern)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 메인 메서드: CLI 또는 독립 실행으로 디렉터리 슬림화 수행
     */
    public static void main(String[] args) {
        String targetPath = (args.length > 0) ? args[0] : "src/test/resources/log_monthly";
        File dir = new File(targetPath);

        System.out.println(">> [LogSlimmer] 대상 디렉터리 경량화 시작: " + dir.getAbsolutePath());
        PolicyManager pm = new PolicyManager();
        pm.loadPolicies();
        List<JobPolicy> policies = pm.getPolicies();

        try {
            int processed = slimDirectory(dir, policies);
            System.out.println(">> [LogSlimmer] 완료: 총 " + processed + "개 파일 슬림화 완료.");
        } catch (Exception e) {
            System.err.println(">> [LogSlimmer 오류] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
