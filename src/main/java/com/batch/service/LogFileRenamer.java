package com.batch.service;

import com.batch.analyzer.LogAnalyzer;
import com.batch.model.JobPolicy;

import java.io.File;
import java.util.List;

/**
 * =====================================================================================
 * [로그 파일명 표준화 전담 클래스 (SRP): LogFileRenamer]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - 미변경 원본 파일명 패턴(rawPattern)을 갖는 로그 파일들을
 *   표준 접두사(filePrefix) 형식으로 변경하는 책임을 전담합니다.
 * =====================================================================================
 */
public class LogFileRenamer {

    /**
     * 원본 파일명 패턴으로 매칭된 로그 파일들을 표준 접두사 파일명으로 일괄 변경
     *
     * @param workFolder 작업 폴더
     * @param policies   JOB 정책 목록
     * @return 변경된 파일 수
     */
    public int rename(File workFolder, List<JobPolicy> policies) {
        if (workFolder == null || !workFolder.exists() || !workFolder.isDirectory()) return 0;
        if (policies == null || policies.isEmpty()) return 0;

        File[] files = workFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
        if (files == null || files.length == 0) return 0;

        int renamedCount = 0;
        for (JobPolicy policy : policies) {
            File target = LogAnalyzer.findTargetFile(files, policy);
            if (target != null && policy.filePrefix != null && !target.getName().startsWith(policy.filePrefix)) {
                File renamedFile = new File(workFolder, policy.filePrefix + target.getName());
                if (target.renameTo(renamedFile)) {
                    System.out.println(">> [파일명 변경] " + target.getName() + " -> " + renamedFile.getName());
                    renamedCount++;
                }
            }
        }
        return renamedCount;
    }
}
