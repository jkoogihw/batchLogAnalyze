package com.batch.analyzer;

import com.batch.model.JobPolicy;

import java.io.File;

/**
 * =====================================================================================
 * [단일 책임 원칙 (SRP): 로그 파일 매칭 및 탐색기]
 * -------------------------------------------------------------------------------------
 * 💡 역할 및 책임:
 * - 파일 목록에서 JOB 정책의 표준 접두사(`filePrefix`), 원본 패턴(`rawPattern`),
 *   와일드카드(`%`) 접미사 패턴을 분석하여 정확한 대상 로그 파일을 찾아내는 책임을 전담합니다.
 * =====================================================================================
 */
public class LogFileLocator {

    /**
     * 정책(JobPolicy)에 매칭되는 로그 파일 탐색
     *
     * @param logFiles 검색 대상 로그 파일 목록
     * @param policy   JOB 정책 객체
     * @return 매칭된 File 객체 (없을 경우 null)
     */
    public File locate(File[] logFiles, JobPolicy policy) {
        if (logFiles == null || policy == null) return null;

        // 1. 표준 접두사 매칭 우선 (예: 01_gagastJob002_*.log)
        if (policy.filePrefix != null && !policy.filePrefix.isEmpty()) {
            for (File f : logFiles) {
                if (f.getName().startsWith(policy.filePrefix)) {
                    return f;
                }
            }
        }

        // 2. 미변경 원본 파일명 패턴(rawPattern) 매칭 Fallback (예: _10702_)
        if (policy.rawPattern != null && !policy.rawPattern.isEmpty()) {
            for (File f : logFiles) {
                String name = f.getName();

                // 와일드카드(%) 포함 패턴 처리 (예: "_11268_%16_1" -> _11268_ 포함 & 16_1로 끝남)
                if (policy.rawPattern.contains("%")) {
                    String[] parts = policy.rawPattern.split("%");
                    String basePattern = parts[0];
                    String suffix = parts.length > 1 ? parts[1] : "";

                    if (name.contains(basePattern)) {
                        String nameWithoutExt = name.contains(".") ? 
                                name.substring(0, name.lastIndexOf('.')) : name;
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
}
