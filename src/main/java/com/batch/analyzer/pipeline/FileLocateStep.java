package com.batch.analyzer.pipeline;

import com.batch.analyzer.LogFileLocator;
import com.batch.model.*;

import java.io.File;

/**
 * =====================================================================================
 * [파이프라인 1단계: 로그 파일 탐색 및 로드 (FileLocateStep)]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * 1. LogFileLocator를 통해 정책에 부합하는 대상 로그 파일을 탐색합니다.
 * 2. 미발견 시 CheckResult에 파일 미발견 상태를 기록하고 파이프라인을 조기 종료(TERMINATE)합니다.
 * 3. 발견 시 LogContent 일급 객체를 로드하여 실행 컨텍스트에 설정하고 다음 단계로 진행합니다.
 * =====================================================================================
 */
public class FileLocateStep implements AnalysisStep {

    private final LogFileLocator fileLocator;

    public FileLocateStep(LogFileLocator fileLocator) {
        this.fileLocator = fileLocator != null ? fileLocator : new LogFileLocator();
    }

    @Override
    public StepResult execute(StepExecutionContext context, CheckResult result) throws Exception {
        JobPolicy policy = context.getPolicy();
        File[] logFiles = context.getJobContext().getLogFiles();

        File targetFile = fileLocator.locate(logFiles, policy);

        if (targetFile == null) {
            String expectedPattern = (policy != null && policy.filePrefix != null ? policy.filePrefix : "") + "*.log (미발견)";
            result.markAsFileNotFound(expectedPattern);
            result.addRuleResult(RuleResult.fail(
                    LogConstants.RULE_NO_ERROR,
                    "로그 파일 존재 여부",
                    RuleType.UNKNOWN.getCode(),
                    "미발견",
                    LogConstants.MSG_FILE_NOT_FOUND
            ));
            return StepResult.terminate("대상 로그 파일 미발견");
        }

        // 도메인 행위 위임 (상태 전이 캡슐화)
        result.attachLogFile(targetFile);
        context.setTargetFile(targetFile);

        // 일급 객체 LogContent 생성 및 컨텍스트에 보관
        LogContent logContent = LogContent.from(targetFile);
        context.setLogContent(logContent);

        return StepResult.next();
    }
}
