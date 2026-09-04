package com.batch.service;

import com.batch.model.JobPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("단위 테스트: LogFileRenamer 원본 로그 파일명 표준화 격리 검증")
class LogFileRenamerTest {

    private final LogFileRenamer renamer = new LogFileRenamer();

    @Test
    @DisplayName("rawPattern에 매칭되는 미변경 파일이 존재할 경우 표준 prefix 파일명으로 변경")
    void testRenameMatchingFile(@TempDir Path tempDir) throws IOException {
        // [Given] 미변경 원본 파일: _10702_sample.log
        Path rawLog = tempDir.resolve("_10702_sample.log");
        Files.writeString(rawLog, "batch log data");

        JobPolicy policy = JobPolicy.builder("01", "JOB01")
                .filePrefix("01_JOB01_")
                .rawPattern("_10702_")
                .build();

        List<JobPolicy> policies = Collections.singletonList(policy);

        // [When]
        int renamedCount = renamer.rename(tempDir.toFile(), policies);

        // [Then]
        assertEquals(1, renamedCount, "1개 파일명이 변경되어야 함");
        assertFalse(Files.exists(rawLog), "원본 파일은 이름이 변경되어 사라져야 함");
        assertTrue(Files.exists(tempDir.resolve("01_JOB01__10702_sample.log")), "표준 접두사가 붙은 파일 생성 확인");
    }

    @Test
    @DisplayName("이미 표준 prefix를 가진 파일은 중복 변경되지 않음 (0건 반환)")
    void testAlreadyStandardizedFile_NoRename(@TempDir Path tempDir) throws IOException {
        Path standardLog = tempDir.resolve("01_JOB01_sample.log");
        Files.writeString(standardLog, "batch log data");

        JobPolicy policy = JobPolicy.builder("01", "JOB01")
                .filePrefix("01_JOB01_")
                .rawPattern("_10702_")
                .build();

        int renamedCount = renamer.rename(tempDir.toFile(), Collections.singletonList(policy));

        assertEquals(0, renamedCount, "이미 표준화된 파일은 변경 건수 0");
    }
}
