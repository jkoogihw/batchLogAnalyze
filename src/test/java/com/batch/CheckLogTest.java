package com.batch;

import com.batch.model.CheckResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;

import static org.junit.Assert.*;

/**
 * CheckLog 실행 파라미터(logFileSrc) 및 폴더 탐색/결과 리포트 생성 테스트
 * 
 * 사용자 요구사항 4가지 조건 검증:
 * 1. logFileSrc 값 지정 시: 해당 폴더에 직접 로그 파일이 있으면 대상 폴더로 설정
 * 2. logFileSrc 폴더에 로그 파일이 없으면: 내부의 최신 날짜 포맷(\\d{6}) 폴더로 대상 설정
 * 3. 날짜 포맷 폴더가 없거나 최종 폴더에 로그 파일이 없으면: 분석 실패(FAIL) 결과 파일 생성
 * 4. logFileSrc 값으로 날짜 포맷(\\d{6})이 주어지면: 기본 경로의 하위 폴더 대상 분석
 */
public class CheckLogTest {

    private static File testTempBase;

    @BeforeClass
    public static void setUp() throws Exception {
        testTempBase = new File("build/test-temp-logs");
        if (testTempBase.exists()) {
            deleteDirectory(testTempBase);
        }
        testTempBase.mkdirs();
    }

    @AfterClass
    public static void tearDown() {
        if (testTempBase != null && testTempBase.exists()) {
            deleteDirectory(testTempBase);
        }
    }

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
     * 1. logFileSrc에 로그 파일이 직접 존재하는 폴더 경로 지정 시 (Condition 1)
     */
    @Test
    public void testCondition1_DirectLogFolderProvided() {
        String logFolder = "src/test/resources/log_samples";
        CheckLog.AnalysisSummary summary = CheckLog.runAnalysis(logFolder);

        assertTrue("직접 로그 파일이 있는 폴더는 분석 성공이어야 함", summary.success);
        assertNotNull("작업 폴더가 설정되어야 함", summary.workFolder);
        assertEquals("폴더명이 log_samples 여야 함", "log_samples", summary.folderName);
        assertTrue("JOB 정책이 1개 이상 로드되어야 함", summary.totalJobs > 0);
        assertEquals("JOB 결과 수가 정책 수와 일치해야 함", summary.totalJobs, summary.results.size());
        assertNotNull("마크다운 리포트 파일이 생성되어야 함", summary.reportFile);
        assertTrue("생성된 리포트 파일이 존재해야 함", summary.reportFile.exists());
    }

    /**
     * 1-1. named parameter (--logFileSrc=...) 형식 지원 검증
     */
    @Test
    public void testCondition1_NamedParameterFormat() {
        String[] args1 = {"--logFileSrc=src/test/resources/log_samples"};
        CheckLog.AnalysisSummary summary1 = CheckLog.runAnalysis(args1);
        assertTrue("Named parameter (--logFileSrc=...) 분석 성공", summary1.success);
        assertEquals("log_samples", summary1.folderName);

        String[] args2 = {"-logFileSrc", "src/test/resources/log_samples"};
        CheckLog.AnalysisSummary summary2 = CheckLog.runAnalysis(args2);
        assertTrue("Named parameter (-logFileSrc value) 분석 성공", summary2.success);
        assertEquals("log_samples", summary2.folderName);
    }

    /**
     * 2. logFileSrc 폴더 자체에는 로그가 없고, 내부 날짜 포맷(\\d{6}) 폴더 중 최신 폴더 선택 (Condition 2)
     */
    @Test
    public void testCondition2_LatestDateSubfolderSelection() throws IOException {
        File parentDir = new File(testTempBase, "nested_logs");
        File oldDateDir = new File(parentDir, "260830");
        File latestDateDir = new File(parentDir, "260901");
        oldDateDir.mkdirs();
        latestDateDir.mkdirs();

        // 260901 폴더에 더미 로그 파일 생성
        File dummyLog = new File(latestDateDir, "test_job_10702.log");
        Files.writeString(dummyLog.toPath(), "DB Insert GA Count : 0\n", StandardCharsets.UTF_8);

        CheckLog.AnalysisSummary summary = CheckLog.runAnalysis(parentDir.getAbsolutePath());

        assertTrue("최신 날짜 폴더가 선택되어 분석 성공이어야 함", summary.success);
        assertEquals("260901 최신 폴더가 선택되어야 함", "260901", summary.folderName);
        assertEquals("260901 디렉터리가 작업 폴더여야 함", latestDateDir.getAbsolutePath(), summary.workFolder.getAbsolutePath());
    }

    /**
     * 3. 날짜 포맷 폴더가 없거나 최종 폴더에 로그 파일이 없는 경우 실패 리포트 생성 (Condition 3)
     */
    @Test
    public void testCondition3_NoLogsOrNoDateFolder_GeneratesFailureReport() throws IOException {
        File emptyDir = new File(testTempBase, "empty_dir_no_logs");
        emptyDir.mkdirs();

        CheckLog.AnalysisSummary summary = CheckLog.runAnalysis(emptyDir.getAbsolutePath());

        assertFalse("로그 파일이 없으므로 success == false 여야 함", summary.success);
        assertEquals("전체 패스 수는 0건이어야 함", 0, summary.passCount);
        assertEquals("전체 실패 수는 전체 JOB 수와 같아야 함", summary.totalJobs, summary.failCount);
        assertNotNull("실패 결과 리포트 파일이 반드시 생성되어야 함", summary.reportFile);
        assertTrue("실패 결과 파일이 디스크에 존재해야 함", summary.reportFile.exists());

        // 리포트 내용 검증
        String reportContent = Files.readString(summary.reportFile.toPath(), StandardCharsets.UTF_8);
        assertTrue("리포트 제목에 대상 폴더명이 포함되어야 함", reportContent.contains("# 배치로그 분석 결과 보고서"));
        assertTrue("모든 JOB에 대해 파일 미존재 메시지가 포함되어야 함", reportContent.contains("해당 JOB의 로그 파일이 존재하지 않습니다."));
    }

    /**
     * 4. logFileSrc 값으로 6자리 날짜 포맷(\\d{6})이 설정된 경우 기본 경로의 하위 폴더 대상 분석 (Condition 4)
     */
    @Test
    public void testCondition4_DateFormattedParamDirectly() throws IOException {
        // 임시 날짜 폴더 생성
        String dateParam = "260902";
        File dateFolder = new File(testTempBase, dateParam);
        dateFolder.mkdirs();

        // 6자리 날짜를 파라미터로 넘겨서 resolveWorkFolder 검증
        File resolved = CheckLog.resolveWorkFolder(dateParam, testTempBase.getAbsolutePath());
        assertNotNull("기본경로 하위의 날짜 폴더가 매핑되어야 함", resolved);
        assertEquals(dateFolder.getAbsolutePath(), resolved.getAbsolutePath());

        // 로그 파일이 없을 때는 실패 리포트 생성 검증
        CheckLog.AnalysisSummary summary = CheckLog.runAnalysis(dateParam);
        assertNotNull("결과 리포트 파일이 생성되어야 함", summary.reportFile);
        assertTrue(summary.reportFile.getName().contains("260902"));
    }
}
