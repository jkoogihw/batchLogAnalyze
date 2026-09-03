package com.batch.analyzer;

import com.batch.analyzer.evaluator.RuleEvaluatorRegistry;
import com.batch.model.*;

import java.io.File;
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
 * 2. 개방-폐쇄 원칙 (OCP) 준수:
 *    - 본 클래스 내부의 복잡한 if-else / switch 분기를 제거하여 결합도를 낮추고
 *      신규 룰 추가 시 본 클래스의 수정 없이 확장이 가능하도록 개선했습니다.
 * 3. 100% 하위 호환성 유지:
 *    - 기존 정적 메서드 시그니처(checkJob, evaluateRule, findTargetFile)를 유지하여
 *      기존 테스트 및 외부 호출부와의 호환성을 완벽하게 보장합니다.
 * =====================================================================================
 */
public class LogAnalyzer {

    private static final LogFileLocator fileLocator = new LogFileLocator();
    private static final HolidayChecker holidayChecker = new HolidayChecker();
    private static final LogDateChecker dateChecker = new LogDateChecker();
    private static final RuleEvaluatorRegistry registry = new RuleEvaluatorRegistry();

    /**
     * 개별 JOB 로그 검증 수행 (기본 폴더명 기반)
     */
    public static CheckResult checkJob(File workFolder, File[] logFiles, JobPolicy policy) {
        String folderName = workFolder != null ? workFolder.getName() : "";
        return checkJob(workFolder, logFiles, policy, folderName, false);
    }

    /**
     * 개별 JOB 로그 검증 수행 (폴더명 및 일자 점검 스킵 옵션 지원)
     *
     * @param workFolder     로그 파일이 위치한 작업 디렉터리
     * @param logFiles       폴더 내 로그 파일 배열
     * @param policy         JOB 정책 메타데이터
     * @param folderName     분석 대상 폴더명 (날짜 해석용)
     * @param skipDateCheck  일자 점검 건너뛰기 여부
     * @return JOB 종합 검증 결과 객체 (CheckResult)
     */
    public static CheckResult checkJob(File workFolder, File[] logFiles, JobPolicy policy, String folderName, boolean skipDateCheck) {
        CheckResult cr = new CheckResult(policy);

        // 1. 파일 매핑 (LogFileLocator에 위임)
        File targetFile = findTargetFile(logFiles, policy);

        if (targetFile == null) {
            cr.fileFound = false;
            cr.fileName = policy.filePrefix + "*.log (미발견)";
            cr.overallPassed = false;

            RuleResult rr = new RuleResult();
            rr.ruleNo = "ERR";
            rr.description = "로그 파일 존재 여부";
            rr.passed = false;
            rr.message = "해당 JOB의 로그 파일이 존재하지 않습니다.";
            cr.addRuleResult(rr);
            return cr;
        }

        cr.fileFound = true;
        cr.fileName = targetFile.getName();

        try {
            // 2. 파일 읽기 (UTF-8)
            String fullText = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
            String[] lines = fullText.split("\\r?\\n");

            // 3. 로그 일자 검증 (LogDateChecker에 위임)
            RuleResult dateResult = dateChecker.checkDate(fullText, policy, folderName, logFiles, skipDateCheck);
            cr.addRuleResult(dateResult);

            // 4. 비영업일 예외 검사 (HolidayChecker에 위임)
            if (holidayChecker.checkAndApply(fullText, policy, cr)) {
                return cr;
            }

            // 5. 개별 규칙 검증 (RuleEvaluatorRegistry 전략 패턴에 위임)
            for (Rule rule : policy.rules) {
                RuleResult rr = evaluateRule(fullText, lines, rule);
                cr.addRuleResult(rr);
            }

        } catch (Exception e) {
            cr.overallPassed = false;
            RuleResult rr = new RuleResult();
            rr.ruleNo = "ERR";
            rr.description = "파일 분석 중 오류 발생";
            rr.passed = false;
            rr.message = "오류 내용: " + e.getMessage();
            cr.addRuleResult(rr);
        }

        return cr;
    }

    /**
     * 단일 규칙 평가 (RuleEvaluatorRegistry에 위임)
     *
     * @param fullText 전체 텍스트
     * @param lines    라인 배열
     * @param rule     평가할 룰
     * @return 룰 평가 결과
     */
    public static RuleResult evaluateRule(String fullText, String[] lines, Rule rule) {
        return registry.evaluate(fullText, lines, rule);
    }

    /**
     * 대상 로그 파일 찾기 (LogFileLocator에 위임)
     *
     * @param logFiles 검색 대상 파일 배열
     * @param policy   JOB 정책
     * @return 매칭된 File 객체
     */
    public static File findTargetFile(File[] logFiles, JobPolicy policy) {
        return fileLocator.locate(logFiles, policy);
    }

    /**
     * 룰 평가기 레지스트리 인스턴스 반환 (확장용)
     */
    public static RuleEvaluatorRegistry getRegistry() {
        return registry;
    }
}
