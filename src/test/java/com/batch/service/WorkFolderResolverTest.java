package com.batch.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("단위 테스트: WorkFolderResolver 4대 조건 기반 폴더 결정 엔진 격리 검증")
class WorkFolderResolverTest {

    private final WorkFolderResolver resolver = new WorkFolderResolver();

    @Test
    @DisplayName("조건 1: 지정된 폴더에 로그 파일이 직접 존재할 때 해당 폴더 반환")
    void testCondition1_DirectLogFolder(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("sample.log");
        Files.writeString(logFile, "test content");

        File resolved = resolver.resolve(tempDir.toString(), tempDir.toString());

        assertNotNull(resolved);
        assertEquals(tempDir.toFile().getAbsolutePath(), resolved.getAbsolutePath());
        assertTrue(resolver.hasLogFiles(resolved));
    }

    @Test
    @DisplayName("조건 2: 서브폴더 중 최신 날짜(6자리) 폴더를 자동 탐색하여 반환")
    void testCondition2_LatestDateSubfolder(@TempDir Path tempDir) throws IOException {
        Path olderFolder = tempDir.resolve("260820");
        Path newerFolder = tempDir.resolve("260822");
        Files.createDirectories(olderFolder);
        Files.createDirectories(newerFolder);

        Files.writeString(olderFolder.resolve("old.log"), "old");
        Files.writeString(newerFolder.resolve("new.log"), "new");

        File resolved = resolver.resolve(tempDir.toString(), tempDir.toString());

        assertNotNull(resolved);
        assertEquals("260822", resolved.getName());
    }

    @Test
    @DisplayName("조건 4: 6자리 날짜(260822) 파라미터 전달 시 baseFolder 하위의 해당 폴더 반환")
    void testCondition4_DateParamWithBaseFolder(@TempDir Path tempDir) throws IOException {
        Path targetDateFolder = tempDir.resolve("260822");
        Files.createDirectories(targetDateFolder);
        Files.writeString(targetDateFolder.resolve("test.log"), "data");

        File resolved = resolver.resolve("260822", tempDir.toString());

        assertNotNull(resolved);
        assertEquals("260822", resolved.getName());
    }

    @Test
    @DisplayName("조건 3: 로그 파일이 없는 경우 실패 리포트용 폴더 결정")
    void testCondition3_NoLogFiles(@TempDir Path tempDir) {
        File resolved = resolver.resolve(tempDir.toString(), tempDir.toString());
        String folderName = resolver.determineFolderName(resolved, tempDir.toString(), tempDir.toString());

        assertNotNull(folderName);
        assertFalse(resolver.hasLogFiles(resolved));
    }
}
