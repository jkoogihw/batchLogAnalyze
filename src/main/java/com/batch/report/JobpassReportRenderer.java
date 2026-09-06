package com.batch.report;

import com.batch.model.CheckResult;
import com.batch.model.RuleResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * =====================================================================================
 * [# Jobpass 텍스트 보고서 전용 렌더러 (JobpassReportRenderer)]
 * -------------------------------------------------------------------------------------
 * 💡 단일 책임 원칙 (SRP):
 * - 배치 로그 분석 결과를 사용자가 지정한 `# Jobpass` 텍스트 복사용 포맷으로 변환합니다.
 * - 콘솔 출력 및 마크다운 보고서 하단 복사용 블록에서 공통으로 사용됩니다.
 * =====================================================================================
 */
public class JobpassReportRenderer {

    /**
     * # Jobpass 텍스트 보고서를 렌더링합니다.
     */
    public static String render(String folderName, List<CheckResult> results, int total, int pass, int fail) {
        StringBuilder sb = new StringBuilder();
        sb.append("==========================================================\n");
        sb.append("# Jobpass\n");

        if (results == null || results.isEmpty()) {
            sb.append("   (분석 결과가 존재하지 않습니다)\n");
            sb.append("==========================================================");
            return sb.toString();
        }

        // JobNo 및 JobName 기준 맵 구성
        Map<String, CheckResult> jobMap = new HashMap<>();
        for (CheckResult cr : results) {
            if (cr.jobNo != null) jobMap.put(cr.jobNo, cr);
            if (cr.jobName != null) jobMap.put(cr.jobName.toLowerCase(), cr);
        }

        // 1. gagastJob002 추천터치고객 통계 데이터 적립
        renderJob01(sb, jobMap.get("01"));

        // 2. GagastJob001 바른활동 실적 통계 데이터 적립
        renderJob02(sb, jobMap.get("02"));

        // 3. smrmJob101 불완전판매 : 로그확인
        renderJob03(sb, jobMap.get("03"));

        // 4. [전일] smrmJob102 불판 확인 SMS 발송(FP?)
        renderJob04(sb, jobMap.get("04"));

        // 5. [전일] smrmJob103 불판 확인 SMS 발송(고객?)
        renderJob05(sb, jobMap.get("05"));

        // 6. smrmJob104 생성파일정보 등록 : 결과로그*
        renderJob06(sb, jobMap.get("06"));

        // 7. smpmJob203 상품목록적재_한생(파일>DB)
        renderJob07(sb, jobMap.get("07"));

        // 8. smpmJob207 보험대리점협회 상품정보요청(수집:파일) : 결과로그
        renderJob08(sb, jobMap.get("08"));

        // 9. smpmJob208 보험대리점협회 상품 적재 (JSON 파일>DB) : 결과로그
        renderJob09(sb, jobMap.get("09"));

        // 10. smpmJob211 상비서 징구현황 메일발송 : 로그확인
        renderJob10(sb, jobMap.get("10"));

        // 11. smpmJob212 확인서 통합적재 - 한생, 한손 건수 로그확인
        renderJob11(sb, jobMap.get("11"), jobMap.get("12"));

        // 12. [전일] smpmJob213 전일자 실행확인
        renderJob12(sb, jobMap.get("13"));

        // 13. smpmJob220 비교상품목록수신(내재화): 로그확인
        renderJob13(sb, jobMap.get("14"));

        // 14. smpcJob002 파기 : 로그확인
        renderJob14(sb, jobMap.get("15"));

        // 15. smpcJob003 협회상품코드설정: 로그확인
        renderJob15(sb, jobMap.get("16"));

        // 16. SmpcJob001 징구요청 : 로그확인
        renderJob16(sb, jobMap.get("17"));

        // 17. smpmJob206 (월간 배치 결과가 포함된 경우 추가 출력)
        if (jobMap.containsKey("18")) {
            renderJob17Monthly(sb, jobMap.get("18"));
        }

        sb.append("==========================================================");
        return sb.toString();
    }

    private static String mark(CheckResult cr) {
        if (cr == null) return "[x]";
        return cr.overallPassed ? "[v]" : "[x]";
    }

    private static RuleResult findRule(CheckResult cr, String ruleNo) {
        if (cr == null || cr.ruleResults == null) return null;
        for (RuleResult rr : cr.ruleResults) {
            if (ruleNo.equals(rr.ruleNo)) return rr;
        }
        return null;
    }

    private static String extractVal(CheckResult cr, String ruleNo, String defaultVal) {
        RuleResult rr = findRule(cr, ruleNo);
        if (rr == null || rr.extractedValue == null || rr.extractedValue.trim().isEmpty()) {
            return defaultVal;
        }
        return rr.extractedValue.trim();
    }

    private static String cleanDigits(String val, String defaultVal) {
        if (val == null || val.trim().isEmpty()) return defaultVal;
        Matcher m = Pattern.compile("(\\d[\\d,]*)").matcher(val);
        if (m.find()) {
            return m.group(1).replace(",", "");
        }
        return defaultVal;
    }

    private static String formatCountWithUnit(String val, String defaultVal) {
        if (val == null || val.trim().isEmpty()) return defaultVal;
        String trimmed = val.trim();
        if (trimmed.endsWith("건.") || trimmed.endsWith("건") || trimmed.endsWith("권.") || trimmed.endsWith("권")) {
            return trimmed;
        }
        return trimmed + "건";
    }

    // 1. gagastJob002
    private static void renderJob01(StringBuilder sb, CheckResult cr) {
        sb.append("1. ").append(mark(cr)).append(" gagastJob002 추천터치고객 통계 데이터 적립 \n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String count = cleanDigits(extractVal(cr, "02", "0"), "0");
        sb.append("   - [DB `Insert` GA Count] : [").append(count).append("]건 \n");
    }

    // 2. GagastJob001
    private static void renderJob02(StringBuilder sb, CheckResult cr) {
        sb.append("2. ").append(mark(cr)).append(" GagastJob001 바른활동 실적 통계 데이터 적립 \n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String gaCount = cleanDigits(extractVal(cr, "02", "00"), "00");
        String fpCount = cleanDigits(extractVal(cr, "03", "0"), "0");
        sb.append("   - [`DB Insert GA Count`] : [").append(gaCount).append("]건 \n");
        sb.append("   - [# `FP누락` 활동 대상[활동건수]] : ").append(fpCount).append(" \n");
    }

    // 3. smrmJob101
    private static void renderJob03(StringBuilder sb, CheckResult cr) {
        sb.append("3. ").append(mark(cr)).append(" smrmJob101 불완전판매 : 로그확인 \n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String val = extractVal(cr, "02", "00 건");
        val = val.replaceAll("\\s*건\\.*$", "").trim();
        sb.append("   - [`불완전판매조사 대상` 신규 추출 건] : ").append(val).append(" 건 \n");
    }

    // 4. smrmJob102
    private static void renderJob04(StringBuilder sb, CheckResult cr) {
        sb.append("4. ").append(mark(cr)).append(" [전일] smrmJob102 불판 확인 SMS 발송(FP?) \n");
        if (cr != null && cr.isHoliday) {
            sb.append("   * [`비영업일에는` 해당 JOB이 수혈되지 않습니다.]\n");
            return;
        }
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String cantSend = cleanDigits(extractVal(cr, "02", "00"), "00");
        String creating = cleanDigits(extractVal(cr, "03", "00"), "00");
        String returnCode = cleanDigits(extractVal(cr, "04", "1"), "1");
        String completed = cleanDigits(extractVal(cr, "05", "00"), "00");

        sb.append("   - [`발송불가` 상태로 전환된 건] TB_SMRM1010 : ").append(cantSend).append(" 건. \n");
        sb.append("   - [발송 `파일생성중` 상태로 전환된 건] TB_SMRM1011 : ").append(creating).append(" 건. \n");
        sb.append("   - [UMS 발송결과 -> `리턴코드` 200] : ").append(returnCode).append("\n");
        sb.append("   - [불완전판매 `SMS FP` 발송 완료 건] TB_SMRM1011 : ").append(completed).append(" 권. \n");
    }

    // 5. smrmJob103
    private static void renderJob05(StringBuilder sb, CheckResult cr) {
        sb.append("5. ").append(mark(cr)).append(" [전일] smrmJob103 불판 확인 SMS 발송(고객?) \n");
        if (cr != null && cr.isHoliday) {
            sb.append("   * [`비영업일에는` 해당 JOB이 수혈되지 않습니다.]\n");
            return;
        }
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String returnCode = cleanDigits(extractVal(cr, "02", "1"), "1");
        String master = cleanDigits(extractVal(cr, "03", "00"), "00");
        String completed = cleanDigits(extractVal(cr, "04", "00"), "00");

        sb.append("   - [UMS 발송결과 -> `리턴코드` 200] : ").append(returnCode).append("\n");
        sb.append("   - [`Master`의 대상 상태를 문자전송(01)] TB_SMRM1010 : ").append(master).append(" 건.\n");
        sb.append("   - [불완전판매 SMS 고객 `발송 완료` 건] TB_SMRM1011 : ").append(completed).append(" 건.\n");
        sb.append("   - [`string[] crTlno`] : 로그확인\n");
    }

    // 6. smrmJob104
    private static void renderJob06(StringBuilder sb, CheckResult cr) {
        sb.append("6. ").append(mark(cr)).append(" smrmJob104 생성파일정보 등록 : 결과로그*\n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String metrics = formatStepMetrics(extractVal(cr, "02", "0/0/0/0"));
        sb.append("   - [`StepName : smrmJob104001`] ").append(metrics).append("\n");
    }

    // 7. smpmJob203
    private static void renderJob07(StringBuilder sb, CheckResult cr) {
        sb.append("7. ").append(mark(cr)).append(" smpmJob203 상품목록적재_한생(파일>DB)\n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String updateVal = cleanDigits(extractVal(cr, "02", "0"), "0");
        String insertVal = cleanDigits(extractVal(cr, "03", "0"), "0");
        sb.append("   - 수정[`updateHliProduct`] : ").append(updateVal).append(" \n");
        sb.append("   - 등록[`insertHliProductList`] : ").append(insertVal).append("\n");
    }

    // 8. smpmJob207
    private static void renderJob08(StringBuilder sb, CheckResult cr) {
        sb.append("8. ").append(mark(cr)).append(" smpmJob207 보험대리점협회 상품정보요청(수집:파일) : 결과로그\n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String totalCount = cleanDigits(extractVal(cr, "03", "3551"), "3551");
        String totalPage = cleanDigits(extractVal(cr, "04", "13"), "13");
        sb.append("   - 06_smpmJob207 : [`HTTP/1.1 200`]\n");
        sb.append("   - totalCount : ").append(totalCount).append(" \n");
        sb.append("   - totalPage : ").append(totalPage).append(" \n");
    }

    // 9. smpmJob208
    private static void renderJob09(StringBuilder sb, CheckResult cr) {
        sb.append("9. ").append(mark(cr)).append(" smpmJob208 보험대리점협회 상품 적재 (JSON 파일>DB) : 결과로그\n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String metrics = formatStepMetrics(extractVal(cr, "02", "0/0/0/0"));
        String skip = cleanDigits(extractVal(cr, "03", "0"), "0");
        sb.append("   - [`StepName : smpmJob208001`] ").append(metrics).append(" \n");
        sb.append("   - [`SmpmSkipPolicy`] : ").append(skip).append("\n");
    }

    // 10. smpmJob211
    private static void renderJob10(StringBuilder sb, CheckResult cr) {
        sb.append("10. ").append(mark(cr)).append(" smpmJob211 상비서 징구현황 메일발송 : 로그확인 \n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String count = cleanDigits(extractVal(cr, "02", "1"), "1");
        sb.append("   - [Email 발송결과 -> `리턴코드 200`] : [").append(count).append("]건\n");
    }

    // 11. smpmJob212 (한생 11 + 한손 12 통합)
    private static void renderJob11(StringBuilder sb, CheckResult cr11, CheckResult cr12) {
        boolean pass = (cr11 == null || cr11.overallPassed) && (cr12 == null || cr12.overallPassed);
        sb.append("11. ").append(pass ? "[v]" : "[x]").append(" smpmJob212 확인서 통합적재 - 한생, 한손 건수 로그확인\n");

        String val11 = (cr11 != null && cr11.fileFound) ? extractVal(cr11, "02", "") : "";
        String val12 = (cr12 != null && cr12.fileFound) ? extractVal(cr12, "02", "") : "";

        sb.append("   - 한생 [`파일 생성 완료`] : ").append(val11.isEmpty() ? "" : val11 + " ").append("\n");
        sb.append("   - 한손 [`파일 생성 완료`] : ").append(val12.isEmpty() ? "" : val12 + " ").append("\n");
    }

    // 12. smpmJob213
    private static void renderJob12(StringBuilder sb, CheckResult cr) {
        sb.append("12. ").append(mark(cr)).append(" [전일] smpmJob213 전일자 실행확인 \n");
        if (cr != null && (cr.isHoliday || (!cr.fileFound && "13".equals(cr.jobNo)))) {
            sb.append("   * [비영업일에는 해당 JOB이 수행되지 않습니다.]\n");
            return;
        }
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        sb.append("   - [비정상 종료 삭제 건수] : [").append(formatCountWithUnit(extractVal(cr, "02", "0건"), "0건")).append("]\n");
        sb.append("   - [상품비교설명확인서 `미징구` 건수] : [").append(formatCountWithUnit(extractVal(cr, "03", "0건"), "0건")).append("]\n");
        sb.append("   - [상품비교설명확인서 첨부여부 상태변경 건수] : [").append(formatCountWithUnit(extractVal(cr, "04", "0건"), "0건")).append("]\n");
        sb.append("   - [상품비교설명확인서 `미징구` 알림발송 대상] : [").append(formatCountWithUnit(extractVal(cr, "05", "0건"), "0건")).append("]\n");
        sb.append("   - [상품비교설명확인서 `발송실패건` 업데이트] : [").append(formatCountWithUnit(extractVal(cr, "06", "0건"), "0건")).append("]\n");
        sb.append("   - [상품비교설명확인서 발송성공건 info] : [").append(formatCountWithUnit(extractVal(cr, "07", "0건"), "0건")).append("]\n");
        sb.append("   - [상품비교설명확인서 발송성공건 일련번호 부여] : [").append(formatCountWithUnit(extractVal(cr, "08", "0건"), "0건")).append("]\n");
    }

    // 13. smpmJob220
    private static void renderJob13(StringBuilder sb, CheckResult cr) {
        sb.append("13. ").append(mark(cr)).append(" smpmJob220 비교상품목록수신(내재화): 로그확인 \n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String val = extractVal(cr, "02", "0건");
        sb.append("   - [ STEP2-3. 기등록 상품추천 `목록 등록하기` ] : ").append(formatCountWithUnit(val, "0건")).append(" \n");
    }

    // 14. smpcJob002
    private static void renderJob14(StringBuilder sb, CheckResult cr) {
        sb.append("14. ").append(mark(cr)).append(" smpcJob002 파기 : 로그확인 \n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String rule02 = extractVal(cr, "02", "0");
        String rule03 = extractVal(cr, "03", "");
        String rule04 = extractVal(cr, "04", "");
        String rule05 = extractVal(cr, "05", "");

        sb.append("   - [`00.상품비교설명확인서` 관리 파기목록 조회 ] : ").append(formatCountWithUnit(rule02, "0건")).append("\n");
        sb.append("   - [`pcicSttsCode=10`] : ").append(rule03.isEmpty() ? "" : rule03 + " ").append("\n");
        sb.append("   - [`pcicSttsCode=20`] : ").append(rule04.isEmpty() ? "" : rule04 + " ").append("\n");
        sb.append("   - [`pcicSttsCode=30`] : ").append(rule05.isEmpty() ? "" : rule05 + " ").append("\n");
        sb.append("   - # baseSize=\n");
    }

    // 15. smpcJob003
    private static void renderJob15(StringBuilder sb, CheckResult cr) {
        sb.append("15. ").append(mark(cr)).append(" smpcJob003 협회상품코드설정: 로그확인\n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String r02 = extractVal(cr, "02", "").replaceAll(",+$", "").trim();
        String r03 = extractVal(cr, "03", "1");
        String r04 = extractVal(cr, "04", "0");
        String r05 = extractVal(cr, "05", "0");

        sb.append("    1. `협회상품기준 미등록 상품 건수`=").append(r02).append(", \n");
        sb.append("    2. 제휴사상품코드 중복 등록 협회상품 건수=").append(cleanDigits(r03, "1")).append(", \n");
        sb.append("    3. [협회상품코드 현행화 대상 불일치 상품 건수]=").append(cleanDigits(r04, "0")).append(", \n");
        sb.append("    4. > 옵션 - 상품정보 초기화 대상 건수=, \n");
        sb.append("    5. > 옵션 - 상품정보 초기화 처리 건수=, \n");
        sb.append("    6. [협회상품코드 현행화 건수]=").append(cleanDigits(r05, "0")).append(", \n");
    }

    // 16. SmpcJob001
    private static void renderJob16(StringBuilder sb, CheckResult cr) {
        sb.append("16. ").append(mark(cr)).append(" SmpcJob001 징구요청 : 로그확인\n");
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String r02 = extractVal(cr, "02", "0");
        String r03 = extractVal(cr, "03", "");
        String r04 = extractVal(cr, "04", "");

        sb.append("   - [ STEP_01.`징구요청대상` 확인서정보 ] : ").append(formatCountWithUnit(r02, "0건")).append("\n");
        sb.append("   - [ `piciBlngCnt : 0` ] : ").append(r03.isEmpty() ? "" : r03 + " ").append("\n");
        sb.append("   - [ `piciBlngCnt : 1` ] : ").append(r04.isEmpty() ? "" : r04 + " ").append("\n");
    }

    // 17. smpmJob206 (월간)
    private static void renderJob17Monthly(StringBuilder sb, CheckResult cr) {
        sb.append("17. ").append(mark(cr)).append(" 206_협회코드및보험사코드수집\n");
        if (cr != null && cr.isMonthlyNotRun) {
            sb.append("   * [월간배치 미실행일 (정상)]\n");
            return;
        }
        if (cr != null && !cr.fileFound) {
            sb.append("   * [로그 파일 미발견 (FAIL)]\n");
            return;
        }
        String r02 = extractVal(cr, "02", "0건");
        String r03 = extractVal(cr, "03", "0건");
        String r04 = extractVal(cr, "04", "0건");
        sb.append("   - [`HTTP/1.1 200`] : ").append(r02).append("\n");
        sb.append("   - [`prodList.size`] : ").append(r03).append("\n");
        sb.append("   - [`TB_SMPM1002.insIntgCode`] : ").append(r04).append("\n");
    }

    /**
     * Step Metrics 문자열을 "0/0/0/0" 또는 간결한 수치 포맷으로 변환합니다.
     */
    private static String formatStepMetrics(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "0/0/0/0";

        // "R:2198 / W:2198 / C:3 / Rollback:0" 패턴 파싱
        Matcher m = Pattern.compile("R:(\\d+)\\s*/\\s*W:(\\d+)\\s*/\\s*C:(\\d+)\\s*/\\s*Rollback:(\\d+)").matcher(raw);
        if (m.find()) {
            return m.group(1) + "/" + m.group(2) + "/" + m.group(3) + "/" + m.group(4);
        }
        return raw;
    }
}
