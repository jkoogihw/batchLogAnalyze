package com.batch.analyzer.pipeline;

import com.batch.analyzer.JobAnalysisContext;
import com.batch.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("분석 파이프라인 AnalysisPipeline 단위 테스트")
class AnalysisPipelineTest {

    @Test
    @DisplayName("파일 미존재 시 파일미발견 상태로 조기 종료")
    void testPipelineFileNotFound() {
        AnalysisPipeline pipeline = AnalysisPipeline.standard();
        JobPolicy policy = JobPolicy.builder("01", "JOB01")
                .jobTitle("미발견 테스트")
                .scheduleTime("03:00")
                .filePrefix("01_not_found")
                .build();

        JobAnalysisContext ctx = JobAnalysisContext.of(new File("260822"), new File[0], policy);
        CheckResult result = pipeline.execute(ctx);

        assertNotNull(result);
        assertFalse(result.isFileFound());
        assertFalse(result.isPassed());
        assertEquals(CheckStatus.FILE_NOT_FOUND, result.getStatus());
    }

    @Test
    @DisplayName("비영업일 로그 감지 시 후속 룰 검증 건너뛰고 정상(HOLIDAY) 종료")
    void testPipelineHolidayShortCircuit(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("01_test_260822.log");
        String content = "2026-08-22 03:00:00 [INFO] 비영업일에는 해당 JOB이 수행되지 않습니다.\n2026-08-22 03:00:01 [INFO] 종료";
        Files.writeString(logFile, content, StandardCharsets.UTF_8);

        JobPolicy policy = JobPolicy.builder("01", "JOB01")
                .jobTitle("비영업일 테스트")
                .scheduleTime("03:00")
                .filePrefix("01_test")
                .build();
        policy.holidayPattern = "비영업일.*수행되지 않습니다";
        // 비영업일일 경우 이 룰은 실행되지 않아야 함
        policy.addRule(Rule.search("NORMAL_TERMINATION", ConditionType.EQUALS_0, "미포함 확인"));

        AnalysisPipeline pipeline = AnalysisPipeline.standard();
        JobAnalysisContext ctx = JobAnalysisContext.of(new File("260822"), new File[]{logFile.toFile()}, policy);
        CheckResult result = pipeline.execute(ctx);

        assertNotNull(result);
        assertTrue(result.isFileFound());
        assertTrue(result.isHoliday());
        assertTrue(result.isPassed());
        assertEquals(CheckStatus.HOLIDAY, result.getStatus());
        // 휴일 로그 우선 점검에 따라 DateCheckStep 및 후속 룰 검증을 건너뛰고 Holiday 룰만 기록됨
        assertEquals(1, result.ruleResults.size()); // Holiday check only
    }

    @Test
    @DisplayName("정상 로그에 대한 전체 파이프라인(일자검증 + 룰평가) 통과")
    void testPipelineNormalPass(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("02_test_260822.log");
        String content = "2026-08-22 03:00:00 [INFO] 배치 시작\n2026-08-22 03:05:00 [INFO] NORMAL_TERMINATION";
        Files.writeString(logFile, content, StandardCharsets.UTF_8);

        JobPolicy policy = JobPolicy.builder("02", "JOB02")
                .jobTitle("정상 통과 테스트")
                .scheduleTime("03:00")
                .filePrefix("02_test")
                .build();
        policy.addRule(Rule.search("NORMAL_TERMINATION", ConditionType.COUNT_CHECK, "정상 종료 확인"));

        AnalysisPipeline pipeline = AnalysisPipeline.standard();
        JobAnalysisContext ctx = JobAnalysisContext.of(new File("260822"), new File[]{logFile.toFile()}, policy);
        CheckResult result = pipeline.execute(ctx);

        assertNotNull(result);
        assertTrue(result.isFileFound());
        assertFalse(result.isHoliday());
        assertTrue(result.isPassed());
        assertEquals(CheckStatus.PASS, result.getStatus());
        assertEquals(2, result.ruleResults.size()); // DateCheck + Rule
    }
}
