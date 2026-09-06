package com.batch.analyzer;

import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.policy.PolicyManager;
import com.batch.report.ReportGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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
 * [원본 로그 기준 분석 보고서(Golden Master Baseline) 수동 생성기]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * - 매번 빌드/테스트 시 자동 실행되는 대상이 아니며, 원본 대용량 로그가 갱신되었을 때
 *   수동으로 단독 실행하여 각 샘플 폴더(src/test/resources/log_*) 내에 기준 보고서를 저장합니다.
 * - report/ 폴더에는 생성하지 않고 해당 log_* 샘플 폴더에서만 형상관리합니다.
 * =====================================================================================
 */
@Disabled("수동 실행 전용: 원본 대용량 로그 갱신 시에만 단독 실행하여 log_* 폴더 내 기준 보고서 생성")
@DisplayName("Golden Master: 원본 대용량 로그 파일 기준 분석 보고서 생성 (수동)")
public class RawLogBaselineGeneratorTest {

    private static List<JobPolicy> policies;

    @BeforeAll
    public static void setUp() throws Exception {
        File rootMeta = new File("src/main/resources/policy_meta.json");
        String json = Files.readString(rootMeta.toPath(), StandardCharsets.UTF_8);
        policies = PolicyManager.parseJsonPolicies(json);
    }

    @Test
    @DisplayName("원본 로그 기준 3대 분석 보고서(Golden Master) 각 샘플 폴더(log_*) 내 생성")
    public void generateRawBaselines() {
        String[] scenarios = {"sample", "monthly", "holiday"};
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

            // [사용자 요구사항] report 폴더에는 생성하지 않고, 해당 샘플로그 폴더로만 저장하여 원격 레포에서 형상관리
            File sampleDirReport = new File(logDir, "원본분석결과_" + scenario + ".md");
            File saved = ReportGenerator.saveMarkdownReport(sampleDirReport, scenario + " (원본)", results, policies.size(), passCount, failCount);
            assertNotNull(saved);

            System.out.println(">> [Golden Master 생성 완료] " + sampleDirReport.getAbsolutePath() + " (PASS: " + passCount + ", FAIL: " + failCount + ")");
        }
    }
}
