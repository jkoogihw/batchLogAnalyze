package com.batch.analyzer;

import com.batch.analyzer.evaluator.RuleEvaluatorRegistry;
import com.batch.config.Config;
import com.batch.model.*;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * =====================================================================================
 * [퍼사드 및 오케스트레이터 (Facade & Orchestrator): LogAnalyzer]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 리팩토링 포인트:
 * 1. 단일 책임 원칙 (SRP) 준수:
 *    - 파일 탐색 로직은 LogFileLocator 로 위임
 *    - 비영업일 예외 검사는 HolidayChecker 로 위임
 *    - 룰 평가 알고리즘은 전략 패턴 기반 RuleEvaluatorRegistry 로 위임
 * 2. 개방-폐쇄 원칙 (OCP) & 의존성 주입 (DI) 지원:
 *    - 생성자를 통해 내부 컴포넌트(LogFileLocator, HolidayChecker 등)를 주입받을 수 있어
 *      단위 테스트 시 가짜(Mock/Fake) 객체를 활용한 격리 테스트가 가능합니다.
 * 3. 100% 하위 호환성 유지:
 *    - 기존 정적 메서드 시그니처(checkJob, evaluateRule, findTargetFile)를 유지하여
 *      기존 테스트 및 외부 호출부와의 호환성을 완벽하게 보장합니다.
 * 4. 인코딩 설정 유연화:
 *    - Config의 'file.encoding' 설정을 참조하여 UTF-8, MS949 등 다양한 로그 인코딩을 지원합니다.
 * =====================================================================================
 */
public class LogAnalyzer {

    private final LogFileLocator fileLocator;
    private final HolidayChecker holidayChecker;
    private final LogDateChecker dateChecker;
    private final RuleEvaluatorRegistry registry;

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
    }

    /**
     * 개별 JOB 로그 검증 수행 (인스턴스 메서드)
     */
    public CheckResult checkJobInstance(File workFolder, File[] logFiles, JobPolicy policy, String folderName, boolean skipDateCheck) {
        CheckResult cr = new CheckResult(policy);

        // 1. 파일 매핑 (LogFileLocator에 위임)
        File targetFile = fileLocator.locate(logFiles, policy);

        if (targetFile == null) {
            cr.markAsFileNotFound((policy != null ? policy.filePrefix : "") + "*.log (미발견)");
            cr.addRuleResult(RuleResult.fail(LogConstants.RULE_NO_ERROR, "로그 파일 존재 여부", 
                    RuleType.UNKNOWN.getCode(), "미발견", LogConstants.MSG_FILE_NOT_FOUND));
            return cr;
        }

        cr.fileFound = true;
        cr.fileName = targetFile.getName();

        try {
            // 2. 파일 읽기 (설정 기반 인코딩 지원)
            String encodingName = Config.get("file.encoding", LogConstants.DEFAULT_ENCODING);
            Charset charset;
            try {
                charset = Charset.forName(encodingName);
            } catch (Exception ex) {
                charset = StandardCharsets.UTF_8;
            }

            String fullText = Files.readString(targetFile.toPath(), charset);
            String[] lines = fullText.split("\\r?\\n");

            // 3. 로그 일자 검증 (LogDateChecker에 위임)
            RuleResult dateResult = dateChecker.checkDate(fullText, policy, folderName, logFiles, skipDateCheck);
            cr.addRuleResult(dateResult);

            // 4. 비영업일 예외 검사 (HolidayChecker에 위임)
            if (holidayChecker.checkAndApply(fullText, policy, cr)) {
                return cr;
            }

            // 5. 개별 규칙 검증 (RuleEvaluatorRegistry 전략 패턴에 위임)
            if (policy != null && policy.rules != null) {
                for (Rule rule : policy.rules) {
                    RuleResult rr = registry.evaluate(fullText, lines, rule);
                    cr.addRuleResult(rr);
                }
            }

        } catch (Exception e) {
            cr.overallPassed = false;
            RuleResult rr = new RuleResult();
            rr.ruleNo = LogConstants.RULE_NO_ERROR;
            rr.description = "파일 분석 중 오류 발생";
            rr.passed = false;
            rr.message = "오류 내용: " + e.getMessage();
            cr.addRuleResult(rr);
        }

        return cr;
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

    // =========================================================================
    // 정적 퍼사드 메서드 (100% 하위 호환성 보장)
    // =========================================================================

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
