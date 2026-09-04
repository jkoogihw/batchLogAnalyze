package com.batch.analyzer;

import com.batch.model.JobPolicy;

import java.io.File;

/**
 * =====================================================================================
 * [매개변수 객체 (Parameter Object): JobAnalysisContext]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 설계 의도 & 리팩토링 포인트:
 * 1. 긴 매개변수 목록(Long Parameter List) 제거:
 *    - 기존 checkJobInstance() 메서드의 5개 인자(workFolder, logFiles, policy, folderName, skipDateCheck)를
 *      단일 컨텍스트 객체로 통합하여 메서드 시그니처를 단순화합니다.
 * 2. 확장성 및 OCP(개방-폐쇄 원칙) 확보:
 *    - 향후 분석 옵션(예: 타임아웃, 엄격 모드, 제외 규칙 목록 등)이 추가되더라도
 *      메서드 시그니처를 수정하지 않고 Context 객체의 필드 확장만으로 대응할 수 있습니다.
 * 3. 빌더 패턴(Builder Pattern) 지원:
 *    - 다양한 파라미터 조합을 가독성 높게 구성할 수 있습니다.
 * =====================================================================================
 */
public class JobAnalysisContext {

    private final File workFolder;
    private final File[] logFiles;
    private final JobPolicy policy;
    private final String folderName;
    private final boolean skipDateCheck;

    private JobAnalysisContext(Builder builder) {
        this.workFolder = builder.workFolder;
        this.logFiles = builder.logFiles != null ? builder.logFiles : new File[0];
        this.policy = builder.policy;
        this.folderName = resolveFolderName(builder.folderName, builder.workFolder);
        this.skipDateCheck = builder.skipDateCheck;
    }

    private static String resolveFolderName(String explicitFolderName, File workFolder) {
        if (explicitFolderName != null && !explicitFolderName.trim().isEmpty()) {
            return explicitFolderName.trim();
        }
        return workFolder != null ? workFolder.getName() : "";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JobAnalysisContext of(File workFolder, File[] logFiles, JobPolicy policy) {
        return builder()
                .workFolder(workFolder)
                .logFiles(logFiles)
                .policy(policy)
                .build();
    }

    public static JobAnalysisContext of(File workFolder, File[] logFiles, JobPolicy policy, String folderName, boolean skipDateCheck) {
        return builder()
                .workFolder(workFolder)
                .logFiles(logFiles)
                .policy(policy)
                .folderName(folderName)
                .skipDateCheck(skipDateCheck)
                .build();
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public File getWorkFolder() {
        return workFolder;
    }

    public File[] getLogFiles() {
        return logFiles;
    }

    public JobPolicy getPolicy() {
        return policy;
    }

    public String getFolderName() {
        return folderName;
    }

    public boolean isSkipDateCheck() {
        return skipDateCheck;
    }

    public String getJobNo() {
        return policy != null ? policy.jobNo : "";
    }

    public String getJobName() {
        return policy != null ? policy.jobName : "";
    }

    // =========================================================================
    // Builder
    // =========================================================================

    public static class Builder {
        private File workFolder;
        private File[] logFiles;
        private JobPolicy policy;
        private String folderName;
        private boolean skipDateCheck = false;

        public Builder workFolder(File workFolder) {
            this.workFolder = workFolder;
            return this;
        }

        public Builder logFiles(File[] logFiles) {
            this.logFiles = logFiles;
            return this;
        }

        public Builder policy(JobPolicy policy) {
            this.policy = policy;
            return this;
        }

        public Builder folderName(String folderName) {
            this.folderName = folderName;
            return this;
        }

        public Builder skipDateCheck(boolean skipDateCheck) {
            this.skipDateCheck = skipDateCheck;
            return this;
        }

        public JobAnalysisContext build() {
            return new JobAnalysisContext(this);
        }
    }
}
