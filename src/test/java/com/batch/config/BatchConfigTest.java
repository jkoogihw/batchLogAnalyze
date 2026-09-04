package com.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("단위 테스트: 타입 안전한 설정 BatchConfig 검증")
class BatchConfigTest {

    @Test
    @DisplayName("기본 설정값 및 타입 변환 정상 동작 검증")
    void testBatchConfigProperties() {
        String baseFolder = BatchConfig.getBaseFolder();
        assertNotNull(baseFolder);

        String policyMetaFile = BatchConfig.getPolicyMetaFile();
        assertNotNull(policyMetaFile);
        assertTrue(policyMetaFile.startsWith("policy_meta"));

        String cutoffTime = BatchConfig.getLogCutoffTime();
        assertNotNull(cutoffTime);
        assertTrue(cutoffTime.matches("\\d{2}:\\d{2}"));

        boolean dateCheckEnabled = BatchConfig.isDateCheckEnabled();
        assertTrue(dateCheckEnabled);

        Charset encoding = BatchConfig.getFileEncoding();
        assertNotNull(encoding);
        assertEquals(StandardCharsets.UTF_8, encoding);
    }
}
