package com.batch.policy;

import com.batch.model.JobPolicy;
import com.batch.model.Rule;
import com.batch.config.Config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 배치 정책 관리자
 * 
 * policy_meta.json 파일에서 JOB 정책을 로드하고 파싱합니다.
 */
public class PolicyManager {
    
    private List<JobPolicy> policies;

    public PolicyManager() {
        this.policies = new ArrayList<>();
    }

    /**
     * 정책 파일 로드
     */
    public void loadPolicies() {
        String baseFolder = Config.get("base.folder", ".");
        String logAnalysisDir = Config.get("log.analysis.dir", "_로그분석");
        String policyFile = Config.get("policy.meta.file", "policy_meta.json");
        
        String metaPath = baseFolder + File.separator + logAnalysisDir + File.separator + policyFile;
        File metaFile = new File(metaPath);
        
        String json = null;
        if (metaFile.exists()) {
            try {
                json = Files.readString(metaFile.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("정책 파일 로드 실패: " + e.getMessage(), e);
            }
        } else {
            // Fallback 1: 루트 경로 확인
            File localRootFile = new File(policyFile);
            if (localRootFile.exists()) {
                try {
                    json = Files.readString(localRootFile.toPath(), StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
            }
            
            // Fallback 2: report 디렉터리 확인
            if (json == null) {
                File reportLocalFile = new File("report", policyFile);
                if (reportLocalFile.exists()) {
                    try {
                        json = Files.readString(reportLocalFile.toPath(), StandardCharsets.UTF_8);
                    } catch (IOException ignored) {}
                }
            }
            
            // Fallback 3: 클래스패스 리소스 확인
            if (json == null) {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(policyFile)) {
                    if (is != null) {
                        json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                } catch (IOException ignored) {}
            }
        }
        
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

            // 비영업일 설정 파싱
            if (mid.contains("\"holidayCheck\"")) {
                Matcher hm = Pattern.compile("\"pattern\"\\s*:\\s*\"([^\"]+)\"").matcher(mid);
                if (hm.find()) {
                    jp.holidayPattern = hm.group(1).replace("\\\\", "\\");
                }
            }

            // 규칙 파싱
            String rulesJson = m.group(6);
            Pattern rPat = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
            Matcher rm = rPat.matcher(rulesJson);
            
            while (rm.find()) {
                String rBlock = rm.group(1);
                Rule r = new Rule();
                r.type = extractJsonField(rBlock, "type");
                r.target = extractJsonField(rBlock, "target");
                if (r.target != null) r.target = r.target.replace("\\n", "\n");
                r.regex = extractJsonField(rBlock, "regex");
                if (r.regex != null) r.regex = r.regex.replace("\\\\", "\\");
                r.stepName = extractJsonField(rBlock, "stepName");
                r.condition = extractJsonField(rBlock, "condition");
                r.description = extractJsonField(rBlock, "description");
                
                String exp = extractJsonField(rBlock, "expectedCount");
                if (exp != null && !exp.isEmpty()) {
                    try { 
                        r.expectedCount = Integer.parseInt(exp); 
                    } catch (NumberFormatException ignored) {}
                }
                jp.rules.add(r);
            }
            list.add(jp);
        }

        return list;
    }

    /**
     * JSON 필드 값 추출
     */
    private static String extractJsonField(String block, String key) {
        // 문자열 값
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1);

        // 숫자 값
        Pattern pNum = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)");
        Matcher mNum = pNum.matcher(block);
        if (mNum.find()) return mNum.group(1);

        return null;
    }

    /**
     * 로드된 정책 목록 조회
     */
    public List<JobPolicy> getPolicies() {
        if (policies.isEmpty()) {
            loadPolicies();
        }
        return policies;
    }

    /**
     * 특정 JOB 정책 조회
     */
    public JobPolicy getPolicyByJobName(String jobName) {
        return policies.stream()
                .filter(p -> p.jobName.equals(jobName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 정책 개수
     */
    public int getPolicyCount() {
        return policies.size();
    }
}
