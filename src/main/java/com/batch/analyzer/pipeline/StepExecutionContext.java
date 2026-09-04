package com.batch.analyzer.pipeline;

import com.batch.analyzer.JobAnalysisContext;
import com.batch.model.JobPolicy;
import com.batch.model.LogContent;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * =====================================================================================
 * [파이프라인 실행 컨텍스트: StepExecutionContext]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - 파이프라인의 각 단계(Step) 간에 공유되는 가변/임시 데이터(대상 파일, LogContent 등)를 안전하게 관리합니다.
 * =====================================================================================
 */
public class StepExecutionContext {

    private final JobAnalysisContext jobContext;
    private File targetFile;
    private LogContent logContent;
    private final Map<String, Object> attributes = new HashMap<>();

    public StepExecutionContext(JobAnalysisContext jobContext) {
        this.jobContext = jobContext;
    }

    public JobAnalysisContext getJobContext() {
        return jobContext;
    }

    public JobPolicy getPolicy() {
        return jobContext != null ? jobContext.getPolicy() : null;
    }

    public File getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(File targetFile) {
        this.targetFile = targetFile;
    }

    public LogContent getLogContent() {
        return logContent;
    }

    public void setLogContent(LogContent logContent) {
        this.logContent = logContent;
    }

    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }
}
