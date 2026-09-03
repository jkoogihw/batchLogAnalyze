package com.batch;

import com.batch.model.CheckResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [컴포넌트 및 파라미터 해석 테스트 (Component & Integration Test) 학습 예제]
 * -------------------------------------------------------------------------------------
 * 💡 학습 포인트:
 * 1. 샌드박스(Sandbox) 디렉터리 격리 패턴:
 *    - 파일 시스템을 조작하는 테스트는 다른 테스트나 로컬 환경에 영향을 주지 않도록
 *      `build/test-temp-logs` 같은 전용 임시 디렉터리를 생성하여 격리합니다.
 *    - `@BeforeAll`에서 깨끗하게 초기화하고, `@AfterAll`에서 완전히 삭제(Tear down)합니다.
 * 2. 비즈니스 요구사항의 4가지 분기 조건(Decision Table) 검증:
 *    - 조건 1: `logFileSrc` 폴더에 .log 파일이 직접 존재하는 경우
 *    - 조건 2: `logFileSrc` 내부에 최신 날짜 포맷(\\d{6}) 서브폴더가 존재하는 경우
 *    - 조건 3: 유효한 로그 파일이나 날짜 폴더가 없어 분석 실패(FAIL) 리포트를 생성해야 하는 경우
 *    - 조건 4: `logFileSrc` 자체가 6자리 날짜(\\d{6})로 주어져 base.folder 하위를 탐색하는 경우
 * 3. CLI 옵션 파싱 (Positional Arguments vs Named Arguments) 검증:
 *    - `--logFileSrc=path`, `-logFileSrc path`, 위치 인자 등 다양한 입력 형식을 검증합니다.
 * =====================================================================================
 */
@DisplayName("컴포넌트 테스트: logFileSrc 실행 파라미터 및 4대 조건 기반 폴더 결정 엔진")
public class CheckLogTest {

    private static File testTempBase;

    /**
     * [테스트 환경 격리 초기화]
     * 빌드 출력 디렉터리 내에 독립된 임시 테스트 폴더를 구성합니다.
     */
    @BeforeAll
    public static void setUp() throws Exception {
        testTempBase = new File("build/test-temp-logs");
        if (testTempBase.exists()) {
            deleteDirectory(testTempBase);
        }
        testTempBase.mkdirs();
    }

    /**
     * [테스트 환경 정리 - 후처리]
     * 모든 테스트 완료 후 잔여 임시 파일들을 깔끔하게 제거하여 부수 효과를 없앱니다.
     */
    @AfterAll
    public static void tearDown() {
        if (testTempBase != null && testTempBase.exists()) {
            deleteDirectory(testTempBase);
        }
    }

    /**
     * 재귀적 디렉터리 삭제 유틸리티
     */
    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    /**
     * ---------------------------------------------------------------------------------
     * [조건 1] 직접 로그 파일이 존재하는 폴더 지정 시
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 대상 폴더에 `.log` 파일이 바로 있을 경우, 하위 탐색 없이 해당 폴더를 즉시 분석 대상(`workFolder`)으로
     *   선정하고 분석 성공(`summary.success == true`)을 반환하는지 확인합니다.
     */
    @Test
    @DisplayName("조건 1: logFileSrc에 로그 파일이 직접 존재하는 폴더 지정 시 정상 분석")
    public void testCondition1_DirectLogFolderProvided() {
        // [Given] 샘플 로그 파일들이 직접 들어있는 디렉터리 경로
        String logFolder = "src/test/resources/log_samples";

        // [When] 분석 실행
        CheckLog.AnalysisSummary summary = CheckLog.runAnalysis(logFolder);

        // [Then] 정상 분석 결과 및 리포트 파일 생성 단언
        assertAll("조건 1 실행 결과 검증",
            () -> assertTrue(summary.success, "직접 로그 파일이 있는 폴더는 분석 성공(true)이어야 함"),
            () -> assertNotNull(summary.workFolder, "작업 폴더가 null이 아니어야 함"),
            () -> assertEquals("log_samples", summary.folderName, "폴더명이 log_samples 여야 함"),
            () -> assertTrue(summary.totalJobs > 0, "JOB 정책이 1개 이상 로드되어야 함"),
            () -> assertEquals(summary.totalJobs, summary.results.size(), "결과 수가 정책 수와 일치해야 함"),
            () -> assertNotNull(summary.reportFile, "마크다운 리포트 파일이 생성되어야 함"),
            () -> assertTrue(summary.reportFile.exists(), "생성된 리포트 파일이 디스크에 존재해야 함")
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [CLI 인자 파싱] 다양한 명명된 파라미터(Named Parameter) 형식 지원
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - `--logFileSrc=value` 형식과 `-logFileSrc value` 형식을 모두 유연하게 파싱하는지 확인합니다.
     */
    @Test
    @DisplayName("CLI 파라미터: --logFileSrc=... 및 -logFileSrc ... 옵션 형식 파싱 검증")
    public void testCondition1_NamedParameterFormat() {
        // [Given & When 1] `--logFileSrc=경로` 형식
        String[] args1 = {"--logFileSrc=src/test/resources/log_samples"};
        CheckLog.AnalysisSummary summary1 = CheckLog.runAnalysis(args1);

        // [Then 1]
        assertAll("--logFileSrc=... 형식 검증",
            () -> assertTrue(summary1.success, "Named parameter (--logFileSrc=...) 분석 성공"),
            () -> assertEquals("log_samples", summary1.folderName)
        );

        // [Given & When 2] `-logFileSrc 경로` 형식
        String[] args2 = {"-logFileSrc", "src/test/resources/log_samples"};
        CheckLog.AnalysisSummary summary2 = CheckLog.runAnalysis(args2);

        // [Then 2]
        assertAll("-logFileSrc ... 형식 검증",
            () -> assertTrue(summary2.success, "Named parameter (-logFileSrc value) 분석 성공"),
            () -> assertEquals("log_samples", summary2.folderName)
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [조건 2] 최신 날짜 포맷(\\d{6}) 서브폴더 자동 선택
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 상위 폴더 자체에는 로그가 없고 하위에 `260830`, `260901` 같은 날짜별 폴더가 있을 때,
     *   가장 최신 날짜(가장 큰 숫자 `260901`)를 자동으로 감지하여 분석 대상 폴더로 선정하는지 검증합니다.
     */
    @Test
    @DisplayName("조건 2: 상위 폴더에 로그가 없을 때 하위 최신 날짜(\\d{6}) 폴더 자동 선택")
    public void testCondition2_LatestDateSubfolderSelection() throws IOException {
        // [Given] 샌드박스 내에 2개의 날짜 서브폴더 생성 (260830, 260901)
        File parentDir = new File(testTempBase, "nested_logs");
        File oldDateDir = new File(parentDir, "260830");
        File latestDateDir = new File(parentDir, "260901");
        oldDateDir.mkdirs();
        latestDateDir.mkdirs();

        // 최신 날짜 폴더(260901)에 더미 로그 파일 생성
        File dummyLog = new File(latestDateDir, "test_job_10702.log");
        Files.writeString(dummyLog.toPath(), "DB Insert GA Count : 0\n", StandardCharsets.UTF_8);

        // [When] 상위 폴더 경로(parentDir)를 파라미터로 넘겨 분석 실행
        CheckLog.AnalysisSummary summary = CheckLog.runAnalysis(parentDir.getAbsolutePath());

        // [Then] 자동으로 최신 날짜인 260901 폴더가 선택되었는지 검증
        assertAll("최신 날짜 서브폴더 자동 선택 검증",
            () -> assertTrue(summary.success, "최신 날짜 폴더가 선택되어 분석 성공이어야 함"),
            () -> assertEquals("260901", summary.folderName, "260901 최신 폴더명이 선택되어야 함"),
            () -> assertEquals(latestDateDir.getAbsolutePath(), summary.workFolder.getAbsolutePath(), "260901 디렉터리가 작업 대상이어야 함")
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [조건 3] 로그 파일이 전혀 없는 경우 실패 리포트(FAIL) 자동 생성
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 대상 폴더가 비어있거나 날짜 폴더 내에 로그가 없을 때, 예외를 던지며 비정상 종료되는 대신
     *   모든 JOB을 FAIL 처리하고 "해당 JOB의 로그 파일이 존재하지 않습니다" 내용의 실패 리포트를
     *   디스크에 정상 생성하는지 확인합니다.
     */
    @Test
    @DisplayName("조건 3: 로그 파일이나 날짜 폴더가 없을 때 분석 실패(FAIL) 리포트 파일 자동 생성")
    public void testCondition3_NoLogsOrNoDateFolder_GeneratesFailureReport() throws IOException {
        // [Given] 빈 디렉터리 준비
        File emptyDir = new File(testTempBase, "empty_dir_no_logs");
        emptyDir.mkdirs();

        // [When] 빈 폴더를 대상으로 분석 실행
        CheckLog.AnalysisSummary summary = CheckLog.runAnalysis(emptyDir.getAbsolutePath());

        // [Then] 실패 상태 요약 및 실패 리포트 마크다운 파일 검증
        assertAll("조건 3 실패 리포트 생성 검증",
            () -> assertFalse(summary.success, "로그 파일이 없으므로 success == false 여야 함"),
            () -> assertEquals(0, summary.passCount, "전체 패스 수는 0건이어야 함"),
            () -> assertEquals(summary.totalJobs, summary.failCount, "전체 실패 수는 전체 JOB 수와 같아야 함"),
            () -> assertNotNull(summary.reportFile, "실패 결과 리포트 파일이 반드시 생성되어야 함"),
            () -> assertTrue(summary.reportFile.exists(), "실패 결과 파일이 디스크에 존재해야 함"),
            () -> {
                String reportContent = Files.readString(summary.reportFile.toPath(), StandardCharsets.UTF_8);
                assertTrue(reportContent.contains("# 배치로그 분석 결과 보고서"), "리포트 제목에 대상 폴더명이 포함되어야 함");
                assertTrue(reportContent.contains("해당 JOB의 로그 파일이 존재하지 않습니다."), "모든 JOB에 대해 파일 미존재 메시지가 포함되어야 함");
            }
        );
    }

    /**
     * ---------------------------------------------------------------------------------
     * [조건 4] 6자리 날짜(\\d{6}) 직접 입력 시 기본 경로(base.folder) 하위 폴더 매핑
     * ---------------------------------------------------------------------------------
     * 💡 학습 포인트:
     * - 사용자가 경로 대신 "260902" 같은 날짜 포맷만 넘겼을 때, `base.folder` 하위의 해당 폴더로
     *   정확하게 매핑되는지 검증합니다.
     */
    @Test
    @DisplayName("조건 4: logFileSrc에 6자리 날짜(\\d{6}) 입력 시 base.folder 하위 폴더 대상 매핑")
    public void testCondition4_DateFormattedParamDirectly() throws IOException {
        // [Given] 임시 기본 경로 하위에 260902 날짜 폴더 생성
        String dateParam = "260902";
        File dateFolder = new File(testTempBase, dateParam);
        dateFolder.mkdirs();

        // [When] resolveWorkFolder 호출로 날짜 폴더 해석
        File resolved = CheckLog.resolveWorkFolder(dateParam, testTempBase.getAbsolutePath());

        // [Then] 기본 경로 하위의 날짜 폴더로 매핑 확인
        assertNotNull(resolved, "기본경로 하위의 날짜 폴더가 매핑되어야 함");
        assertEquals(dateFolder.getAbsolutePath(), resolved.getAbsolutePath());

        // [When] 로그가 없는 상태에서 분석 실행
        CheckLog.AnalysisSummary summary = CheckLog.runAnalysis(dateParam);

        // [Then] 해당 날짜명으로 실패 리포트 파일이 생성되었는지 확인
        assertNotNull(summary.reportFile, "결과 리포트 파일이 생성되어야 함");
        assertTrue(summary.reportFile.getName().contains("260902"), "리포트 파일명에 날짜가 포함되어야 함");
    }

    /**
     * ---------------------------------------------------------------------------------
     * [CLI 옵션 검증] --skipDateCheck / --allLogs 옵션 처리 검증
     * ---------------------------------------------------------------------------------
     */
    @Test
    @DisplayName("CLI 옵션: --skipDateCheck 전달 시 일자 검증을 생략하고 정상 분석 수행")
    public void testRunAnalysis_WithSkipDateCheckOption() throws IOException {
        // [Given] 테스트용 로그 파일 생성 (과거 일자 2020-01-01이지만 skipDateCheck로 PASS 기대)
        File skipTestFolder = new File(testTempBase, "skip_date_test");
        skipTestFolder.mkdirs();
        File logFile = new File(skipTestFolder, "test_job001_sample_1.log");
        Files.writeString(logFile.toPath(), "2020-01-01 03:00:00.000 INFO SUCCESS\n", StandardCharsets.UTF_8);

        // [When] skipDateCheck = true 로 분석 실행
        CheckLog.AnalysisSummary summary = CheckLog.runAnalysis(skipTestFolder.getAbsolutePath(), true);

        // [Then]
        assertAll("skipDateCheck 옵션 실행 검증",
            () -> assertTrue(summary.success, "분석 성공"),
            () -> assertTrue(summary.passCount > 0, "JOB 01에 대해 패스 항목 존재"),
            () -> assertNotNull(summary.reportFile, "리포트 파일 생성 확인")
        );
    }
}
