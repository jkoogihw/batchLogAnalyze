package com.batch.analyzer;

import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.policy.PolicyManager;
import com.batch.report.ReportGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [원본 로그 기준 분석 보고서(Golden Master Baseline) 생성기]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * 경량화(Slimming) 이전의 원본 대용량 로그 파일 전체를 대상으로 3대 시나리오 분석을
 * 전수 수행하고, 기준 보고서(report/원본분석결과_*.md)를 생성합니다.
 * =====================================================================================
 */
@DisplayName("Golden Master: 원본 대용량 로그 파일 기준 분석 보고서 생성")
public class RawLogBaselineGeneratorTest {

    private static List<JobPolicy> policies;
    private static File reportDir;

    @BeforeAll
    public static void setUp() throws Exception {
        File rootMeta = new File("src/main/resources/policy_meta.json");
        String json = Files.readString(rootMeta.toPath(), StandardCharsets.UTF_8);
        policies = PolicyManager.parseJsonPolicies(json);

        reportDir = new File("report");
        if (!reportDir.exists()) {
            reportDir.mkdirs();
        }
    }

    @Test
    @DisplayName("[Step 1] 원본 로그 기준 3대 분석 보고서(Golden Master) 일괄 생성")
    public void generateRawBaselines() {
        String[] scenarios = {"sample", "monthly", "nonworkday"};
        String[] dirNames = {"log_samples", "log_monthly", "log_holiday"};

        for (int i = 0; i < scenarios.length; i++) {
            String scenario = scenarios[i];
            String dirName = dirNames[i];

            File logDir = new File("src/test/resources/" + dirName);
            assertTrue(logDir.exists() && logDir.isDirectory(), dirName + " 디렉터리가 존재해야 합니다");

            File[] logFiles = logDir.listFiles((d, name) -> name.endsWith(".log"));
            assertNotNull(logFiles, dirName + " 파일 목록이 존재해야 합니다");

            List<CheckResult> results = new ArrayList<>();
            int passCount = 0;
            int failCount = 0;

            for (JobPolicy policy : policies) {
                CheckResult cr = LogAnalyzer.checkJob(logDir, logFiles, policy);
                results.add(cr);
                if (cr.overallPassed) passCount++;
                else failCount++;
            }

            File targetReport = new File(reportDir, "원본분석결과_" + scenario + ".md");
            File saved = ReportGenerator.saveMarkdownReport(targetReport, scenario + " (원본)", results, policies.size(), passCount, failCount);
            assertNotNull(saved);

            // [사용자 요구사항] 원본분석 보고서 파일을 해당 샘플로그 폴더로 저장하여 원격 레포에서 형상관리
            File sampleDirReport = new File(logDir, "원본분석결과_" + scenario + ".md");
            ReportGenerator.saveMarkdownReport(sampleDirReport, scenario + " (원본)", results, policies.size(), passCount, failCount);

            System.out.println(">> [Golden Master 생성 완료] " + saved.getAbsolutePath() + " & " + sampleDirReport.getAbsolutePath() + " (PASS: " + passCount + ", FAIL: " + failCount + ")");
        }
    }
}
