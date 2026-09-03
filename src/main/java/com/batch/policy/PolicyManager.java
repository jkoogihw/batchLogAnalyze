package com.batch.policy;

import com.batch.config.Config;
import com.batch.model.JobPolicy;
import com.batch.model.Rule;
import com.batch.policy.loader.CompositePolicyLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * =====================================================================================
 * [배치 정책 관리자 (PolicyManager)]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 리팩토링 포인트:
 * 1. DIP (의존 역전 원칙) 준수:
 *    - 정책 파일 로딩 책임을 PolicyLoader 인터페이스로 분리하여 유연한 주입(DI) 지원
 * 2. SRP (단일 책임 원칙) 준수:
 *    - 데이터 로딩은 PolicyLoader가, 파싱 및 인메모리 관리는 PolicyManager가 담당
 * =====================================================================================
 */
public class PolicyManager {

    private final PolicyLoader policyLoader;
    private List<JobPolicy> policies;

    public PolicyManager() {
        this(new CompositePolicyLoader());
    }

    public PolicyManager(PolicyLoader policyLoader) {
        this.policyLoader = policyLoader != null ? policyLoader : new CompositePolicyLoader();
        this.policies = new ArrayList<>();
    }

    /**
     * 설정에 따른 정책 파일 로드
     */
    public void loadPolicies() {
        String baseFolder = Config.get("base.folder", ".");
        String logAnalysisDir = Config.get("log.analysis.dir", "_로그분석");
        String policyFile = Config.get("policy.meta.file", "policy_meta.json");

        String metaPath = baseFolder + File.separator + logAnalysisDir + File.separator + policyFile;

        // PolicyLoader에 로딩 위임 (외부 경로 -> 클래스패스 -> 루트 순차 탐색)
        String json = policyLoader.load(metaPath);

        if (json == null || json.isEmpty()) {
            throw new RuntimeException(
                    "정책 메타데이터 파일이 존재하지 않습니다: " + metaPath + 
                    "\napplication.properties에서 경로 설정을 확인하거나 policy_meta.json 파일을 확인하세요.");
        }

        this.policies = parseJsonPolicies(json);
        if (policies.isEmpty()) {
            throw new RuntimeException("정책 파일에서 유효한 정책을 찾을 수 없습니다: " + metaPath);
        }
    }

    /**
     * 경량 내장 JSON 정책 파서
     */
    public static List<JobPolicy> parseJsonPolicies(String json) {
        List<JobPolicy> list = new ArrayList<>();
        Pattern jobPattern = Pattern.compile(
                "\\{\\s*\"jobNo\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"jobName\"\\s*:\\s*\"([^\"]+)\"\\s*," +
                "\\s*\"jobTitle\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"filePrefix\"\\s*:\\s*\"([^\"]+)\"(.*?)" +
                "\"rules\"\\s*:\\s*\\[(.*?)\\]\\s*\\}", Pattern.DOTALL);

        Matcher m = jobPattern.matcher(json);

        while (m.find()) {
            JobPolicy jp = new JobPolicy();
            jp.jobNo = m.group(1);
            jp.jobName = m.group(2);
            jp.jobTitle = m.group(3);
            jp.filePrefix = m.group(4);

            String mid = m.group(5);
            jp.rawPattern = extractJsonField(mid, "rawPattern");
            jp.scheduleType = extractJsonField(mid, "scheduleType");
            jp.scheduleTime = extractJsonField(mid, "scheduleTime");
            
            String monthlyDayStr = extractNumberField(mid, "monthlyLogDay");
            if (monthlyDayStr != null) {
                try {
                    jp.monthlyLogDay = Integer.parseInt(monthlyDayStr);
                } catch (NumberFormatException ignored) {}
            }

            if (mid != null && mid.contains("holidayCheck")) {
                Pattern holidayPat = Pattern.compile("\"holidayCheck\"\\s*:\\s*\\{[^}]*\"pattern\"\\s*:\\s*\"([^\"]+)\"");
                Matcher hm = holidayPat.matcher(mid);
                if (hm.find()) {
                    jp.holidayPattern = hm.group(1).replace("\\\\", "\\");
                }
            }

            String rulesBlock = m.group(6);
            jp.rules = parseRules(rulesBlock);

            list.add(jp);
        }

        return list;
    }

    /**
     * 단일 문자열 필드 추출 헬퍼
     */
    private static String extractJsonField(String jsonBlock, String fieldName) {
        if (jsonBlock == null) return null;
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(jsonBlock);
        if (m.find()) {
            return m.group(1).replace("\\\\", "\\");
        }
        return null;
    }

    /**
     * Rules JSON 블록 파싱
     */
    private static List<Rule> parseRules(String rulesBlock) {
        List<Rule> rules = new ArrayList<>();
        if (rulesBlock == null || rulesBlock.trim().isEmpty()) {
            return rules;
        }

        Pattern rulePattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher rm = rulePattern.matcher(rulesBlock);
        int autoSeq = 2;

        while (rm.find()) {
            String ruleContent = rm.group(1);
            Rule rule = new Rule();

            rule.ruleNo = extractField(ruleContent, "ruleNo");
            if (rule.ruleNo == null || rule.ruleNo.trim().isEmpty()) {
                rule.ruleNo = String.format("%02d", autoSeq);
            }
            autoSeq++;

            rule.type = extractField(ruleContent, "type");
            rule.target = extractField(ruleContent, "target");
            rule.condition = extractField(ruleContent, "condition");
            rule.description = extractField(ruleContent, "description");
            rule.regex = extractField(ruleContent, "regex");
            rule.stepName = extractField(ruleContent, "stepName");

            String expectedCountStr = extractNumberField(ruleContent, "expectedCount");
            if (expectedCountStr != null) {
                try {
                    rule.expectedCount = Integer.parseInt(expectedCountStr);
                } catch (NumberFormatException ignored) {}
            }

            rules.add(rule);
        }

        return rules;
    }

    private static String extractField(String block, String fieldName) {
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(block);
        if (m.find()) {
            return m.group(1).replace("\\\\", "\\");
        }
        return null;
    }

    private static String extractNumberField(String block, String fieldName) {
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(block);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public List<JobPolicy> getPolicies() {
        return Collections.unmodifiableList(policies);
    }
}
