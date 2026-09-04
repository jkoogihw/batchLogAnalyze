package com.batch.analyzer;

import com.batch.model.JobPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("매개변수 객체 JobAnalysisContext 단위 테스트")
class JobAnalysisContextTest {

    @Test
    @DisplayName("팩토리 메서드 및 기본값 설정 검증")
    void testFactoryMethods() {
        File workFolder = new File("260822");
        File[] logFiles = new File[]{new File("test.log")};
        JobPolicy policy = JobPolicy.builder("01", "JOB_TEST")
                .jobTitle("테스트 JOB")
                .scheduleTime("03:00")
                .filePrefix("01_test")
                .build();

        JobAnalysisContext ctx = JobAnalysisContext.of(workFolder, logFiles, policy);

        assertNotNull(ctx);
        assertEquals("260822", ctx.getFolderName());
        assertEquals("01", ctx.getJobNo());
        assertEquals("JOB_TEST", ctx.getJobName());
        assertEquals(1, ctx.getLogFiles().length);
        assertFalse(ctx.isSkipDateCheck());
    }

    @Test
    @DisplayName("빌더를 통한 커스텀 파라미터 구성 검증")
    void testBuilder() {
        JobPolicy policy = JobPolicy.builder("02", "JOB_CUSTOM")
                .jobTitle("커스텀 JOB")
                .scheduleTime("05:00")
                .filePrefix("02_custom")
                .build();

        JobAnalysisContext ctx = JobAnalysisContext.builder()
                .folderName("CUSTOM_DIR")
                .policy(policy)
                .skipDateCheck(true)
                .build();

        assertNotNull(ctx);
        assertEquals("CUSTOM_DIR", ctx.getFolderName());
        assertTrue(ctx.isSkipDateCheck());
        assertEquals(0, ctx.getLogFiles().length);
    }
}
