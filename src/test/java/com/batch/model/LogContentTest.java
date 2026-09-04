package com.batch.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("일급 객체 LogContent 단위 테스트")
class LogContentTest {

    @Test
    @DisplayName("문자열로부터 LogContent 생성 및 라인 분할 검증")
    void testCreateFromString() {
        String text = "Line 1\r\nLine 2\nLine 3";
        LogContent content = LogContent.of(text);

        assertNotNull(content);
        assertEquals(text, content.getFullText());
        assertEquals(3, content.getLineCount());
        assertEquals("Line 1", content.getLines().get(0));
        assertEquals("Line 2", content.getLines().get(1));
        assertEquals("Line 3", content.getLines().get(2));
        assertNull(content.getFile());
        assertEquals("", content.getFileName());
    }

    @Test
    @DisplayName("파일로부터 LogContent 생성 및 인코딩 처리 검증")
    void testCreateFromFile(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("sample_batch.log");
        String contentText = "2026-08-22 03:00:00 [INFO] 배치 시작\n2026-08-22 03:05:00 [INFO] 배치 완료";
        Files.writeString(logFile, contentText, StandardCharsets.UTF_8);

        LogContent logContent = LogContent.from(logFile.toFile(), StandardCharsets.UTF_8);

        assertNotNull(logContent);
        assertEquals(2, logContent.getLineCount());
        assertEquals("sample_batch.log", logContent.getFileName());
        assertTrue(logContent.contains("배치 시작"));
        assertFalse(logContent.contains("존재하지 않는 텍스트"));
        assertTrue(logContent.matches(Pattern.compile("배치\\s+완료")));
    }

    @Test
    @DisplayName("상위 N개 헤더 라인 추출 검증")
    void testGetHeader() {
        String text = "Header 1\nHeader 2\nBody 1\nBody 2\nFooter";
        LogContent content = LogContent.of(text);

        String header2 = content.getHeader(2);
        assertTrue(header2.contains("Header 1"));
        assertTrue(header2.contains("Header 2"));
        assertFalse(header2.contains("Body 1"));

        assertEquals(text, content.getHeader(100)); // 전체 줄수 초과 시 전체 반환
    }

    @Test
    @DisplayName("불변성(Immutability) 검증: lines 리스트 수정 불가")
    void testImmutability() {
        LogContent content = LogContent.of("A\nB\nC");
        assertThrows(UnsupportedOperationException.class, () -> {
            content.getLines().add("D");
        });
    }

    @Test
    @DisplayName("존재하지 않는 파일 로드 시 IllegalArgumentException 발생")
    void testNonExistentFile() {
        File fake = new File("non_existent_path/fake.log");
        assertThrows(IllegalArgumentException.class, () -> {
            LogContent.from(fake);
        });
    }
}
