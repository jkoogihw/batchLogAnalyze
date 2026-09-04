package com.batch.analyzer;

import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.RuleResult;
import com.batch.model.RuleType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * =====================================================================================
 * [단일 책임 원칙 (SRP): 비영업일 예외 검사기]
 * -------------------------------------------------------------------------------------
 * 💡 역할 및 책임:
 * - 배치 로그 텍스트에서 휴일/비영업일 안내 메시지(예: "비영업일에는 해당 JOB이 수행되지 않습니다")가
 *   존재하는지 정규식으로 검사하고, 발견 시 정상(PASS)으로 마킹하는 책임을 전담합니다.
 * =====================================================================================
 */
public class HolidayChecker {

    /**
     * 비영업일 예외 조건 검사 및 상태 적용
     *
     * @param fullText 전체 로그 텍스트
     * @param policy   JOB 정책
     * @param cr       결과를 반영할 CheckResult 객체
     * @return 비영업일 예외가 적용되었는지 여부 (true/false)
     */
    public boolean checkAndApply(String fullText, JobPolicy policy, CheckResult cr) {
        if (fullText == null || policy == null || cr == null) return false;
        if (policy.holidayPattern == null || policy.holidayPattern.isEmpty()) return false;

        Pattern hp = Pattern.compile(policy.holidayPattern);
        Matcher hm = hp.matcher(fullText);

        if (hm.find()) {
            String holidayDetail = hm.group(0);
            cr.markAsHoliday(holidayDetail);

            RuleResult rr = RuleResult.builder()
                    .description("비영업일 예외 확인")
                    .type(RuleType.HOLIDAY)
                    .target(policy.holidayPattern)
                    .extractedValue(holidayDetail)
                    .condition("비영업일 수행 건너뜀 (정상)")
                    .passed(true)
                    .message("비영업일 안내 로그 감지됨 -> 정상 판정")
                    .build();

            cr.addRuleResult(rr);
            return true;
        }

        return false;
    }
}
