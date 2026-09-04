package com.batch.analyzer;

import com.batch.analyzer.evaluator.RuleEvaluatorRegistry;
import com.batch.analyzer.pipeline.AnalysisPipeline;
import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.Rule;
import com.batch.model.RuleResult;

import java.io.File;

/**
 * =====================================================================================
 * [퍼사드 및 오케스트레이터 (Facade & Orchestrator): LogAnalyzer]
 * -------------------------------------------------------------------------------------
 * 💡 고급 OOP 리팩토링 포인트:
 * 1. 책임 연쇄 패턴 (Chain of Responsibility) 및 파이프라인 엔진 도입:
 *    - 기존의 절차적 실행 로직을 AnalysisPipeline 엔진에 위임하여 완벽한 OCP를 달성합니다.
 * 2. 매개변수 객체 (Parameter Object) 지원:
 *    - JobAnalysisContext를 도입하여 5개의 개별 인자를 단일 컨텍스트로 통합하고 확장성을 확보했습니다.
 * 3. 100% 완벽한 하위 호환성 유지:
 *    - 기존 정적 메서드(checkJob, evaluateRule, findTargetFile) 및 다중 인자 인스턴스 메서드를
 *      그대로 유지하여 기존 테스트 및 외부 호출부와의 호환성을 완벽하게 보장합니다.
 * =====================================================================================
 */
public class LogAnalyzer {

    private final LogFileLocator fileLocator;
    private final HolidayChecker holidayChecker;
    private final LogDateChecker dateChecker;
    private final RuleEvaluatorRegistry registry;
    private final AnalysisPipeline pipeline;

    // 싱글톤 기본 인스턴스 (정적 메서드 위임용)
    private static final LogAnalyzer DEFAULT_INSTANCE = new LogAnalyzer();

    public LogAnalyzer() {
        this(new LogFileLocator(), new HolidayChecker(), new LogDateChecker(), new RuleEvaluatorRegistry());
    }

    public LogAnalyzer(LogFileLocator fileLocator, HolidayChecker holidayChecker, LogDateChecker dateChecker, RuleEvaluatorRegistry registry) {
        this.fileLocator = fileLocator != null ? fileLocator : new LogFileLocator();
        this.holidayChecker = holidayChecker != null ? holidayChecker : new HolidayChecker();
        this.dateChecker = dateChecker != null ? dateChecker : new LogDateChecker();
        this.registry = registry != null ? registry : new RuleEvaluatorRegistry();
        this.pipeline = AnalysisPipeline.standard(this.fileLocator, this.holidayChecker, this.dateChecker, this.registry);
    }

    public LogAnalyzer(AnalysisPipeline pipeline) {
        this.fileLocator = new LogFileLocator();
        this.holidayChecker = new HolidayChecker();
        this.dateChecker = new LogDateChecker();
        this.registry = new RuleEvaluatorRegistry();
        this.pipeline = pipeline != null ? pipeline : AnalysisPipeline.standard();
    }

    /**
     * 매개변수 객체(JobAnalysisContext) 기반 검증 수행 (신규 권장 방식)
     */
    public CheckResult checkJobInstance(JobAnalysisContext context) {
        return pipeline.execute(context);
    }

    /**
     * 개별 JOB 로그 검증 수행 (하위 호환성 유지용)
     */
    public CheckResult checkJobInstance(File workFolder, File[] logFiles, JobPolicy policy, String folderName, boolean skipDateCheck) {
        JobAnalysisContext context = JobAnalysisContext.of(workFolder, logFiles, policy, folderName, skipDateCheck);
        return checkJobInstance(context);
    }

    public RuleResult evaluateRuleInstance(String fullText, String[] lines, Rule rule) {
        return registry.evaluate(fullText, lines, rule);
    }

    public File findTargetFileInstance(File[] logFiles, JobPolicy policy) {
        return fileLocator.locate(logFiles, policy);
    }

    public RuleEvaluatorRegistry getRegistryInstance() {
        return registry;
    }

    public AnalysisPipeline getPipelineInstance() {
        return pipeline;
    }

    // =========================================================================
    // 정적 퍼사드 메서드 (100% 하위 호환성 보장)
    // =========================================================================

    public static CheckResult checkJob(JobAnalysisContext context) {
        return DEFAULT_INSTANCE.checkJobInstance(context);
    }

    public static CheckResult checkJob(File workFolder, File[] logFiles, JobPolicy policy) {
        String folderName = workFolder != null ? workFolder.getName() : "";
        return checkJob(workFolder, logFiles, policy, folderName, false);
    }

    public static CheckResult checkJob(File workFolder, File[] logFiles, JobPolicy policy, String folderName, boolean skipDateCheck) {
        return DEFAULT_INSTANCE.checkJobInstance(workFolder, logFiles, policy, folderName, skipDateCheck);
    }

    public static RuleResult evaluateRule(String fullText, String[] lines, Rule rule) {
        return DEFAULT_INSTANCE.evaluateRuleInstance(fullText, lines, rule);
    }

    public static File findTargetFile(File[] logFiles, JobPolicy policy) {
        return DEFAULT_INSTANCE.findTargetFileInstance(logFiles, policy);
    }

    public static RuleEvaluatorRegistry getRegistry() {
        return DEFAULT_INSTANCE.getRegistryInstance();
    }
}
