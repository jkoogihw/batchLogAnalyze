package com.batch.model;

import com.batch.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * =====================================================================================
 * [일급 객체 / 값 객체 (Value Object): LogContent]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 설계 의도 & 리팩토링 포인트:
 * 1. 원시값 집착(Primitive Obsession) 및 데이터 뭉치(Data Clump) 제거:
 *    - 기존에 여러 계층으로 흩어져 함께 전달되던 'String fullText'와 'String[] lines'를
 *      하나의 의미 있는 도메인 불변 객체로 캡슐화합니다.
 * 2. 저수준 파일 I/O 및 문자셋 인코딩 캡슐화:
 *    - 파일 읽기, Charset 해석, 줄바꿈 단위 분할 책임을 내부에 은닉하여 외부 클라이언트(LogAnalyzer 등)가
 *      I/O 세부사항에 의존하지 않도록 합니다.
 * 3. 풍부한 도메인 편의 메서드 제공:
 *    - contains(), matches(), getHeaderLines() 등 로그 텍스트 탐색 행위를 제공합니다.
 * 4. 불변성(Immutability) 보장:
 *    - 내부 라인 리스트는 unmodifiableList로 보호되어 생성 후 상태가 안전하게 보존됩니다.
 * =====================================================================================
 */
public class LogContent {

    private final File file;
    private final String fullText;
    private final List<String> lines;
    private final String[] linesArray; // 성능 최적화용 캐시

    private LogContent(File file, String fullText, List<String> lines) {
        this.file = file;
        this.fullText = fullText != null ? fullText : "";
        this.lines = lines != null ? Collections.unmodifiableList(new ArrayList<>(lines)) : Collections.emptyList();
        this.linesArray = this.lines.toArray(new String[0]);
    }

    /**
     * 파일과 캐릭터셋을 전달받아 LogContent 인스턴스를 생성하는 팩토리 메서드
     */
    public static LogContent from(File file, Charset charset) throws IOException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("로그 파일이 존재하지 않습니다: " + (file != null ? file.getAbsolutePath() : "null"));
        }
        Charset targetCharset = charset != null ? charset : resolveConfiguredCharset();
        String fullText = Files.readString(file.toPath(), targetCharset);
        List<String> lines = parseLines(fullText);
        return new LogContent(file, fullText, lines);
    }

    /**
     * 시스템 설정(file.encoding) 기반으로 파일을 읽어 LogContent를 생성하는 팩토리 메서드
     */
    public static LogContent from(File file) throws IOException {
        return from(file, resolveConfiguredCharset());
    }

    /**
     * 순수 문자열로부터 LogContent 인스턴스를 생성하는 팩토리 메서드 (단위 테스트 및 모의용)
     */
    public static LogContent of(String fullText) {
        List<String> lines = parseLines(fullText);
        return new LogContent(null, fullText, lines);
    }

    /**
     * 파일과 텍스트 내용을 직접 지정하여 생성하는 팩토리 메서드
     */
    public static LogContent of(File file, String fullText) {
        List<String> lines = parseLines(fullText);
        return new LogContent(file, fullText, lines);
    }

    private static List<String> parseLines(String fullText) {
        if (fullText == null || fullText.isEmpty()) {
            return Collections.emptyList();
        }
        String[] split = fullText.split("\\r?\\n");
        return Arrays.asList(split);
    }

    private static Charset resolveConfiguredCharset() {
        String encodingName = Config.get("file.encoding", LogConstants.DEFAULT_ENCODING);
        try {
            return Charset.forName(encodingName);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    // =========================================================================
    // 도메인 질의 및 편의 메서드
    // =========================================================================

    public File getFile() {
        return file;
    }

    public String getFileName() {
        return file != null ? file.getName() : "";
    }

    public String getFullText() {
        return fullText;
    }

    public List<String> getLines() {
        return lines;
    }

    public String[] getLinesArray() {
        return linesArray;
    }

    public int getLineCount() {
        return lines.size();
    }

    public boolean isEmpty() {
        return fullText == null || fullText.isEmpty();
    }

    /**
     * 특정 텍스트 포함 여부 검사
     */
    public boolean contains(String target) {
        if (target == null || target.isEmpty()) return false;
        return fullText.contains(target);
    }

    /**
     * 정규표현식 매칭 여부 검사
     */
    public boolean matches(Pattern pattern) {
        if (pattern == null || fullText.isEmpty()) return false;
        return pattern.matcher(fullText).find();
    }

    /**
     * 정규표현식 매칭 결과 Matcher 반환
     */
    public Matcher matcher(Pattern pattern) {
        if (pattern == null) throw new IllegalArgumentException("Pattern cannot be null");
        return pattern.matcher(fullText);
    }

    /**
     * 상위 N개 라인을 문자열로 추출 (헤더 스캔용)
     */
    public String getHeader(int maxLines) {
        if (lines.isEmpty() || maxLines <= 0) return "";
        int limit = Math.min(lines.size(), maxLines);
        return String.join("\n", lines.subList(0, limit));
    }

    @Override
    public String toString() {
        return "LogContent{" +
                "fileName='" + getFileName() + '\'' +
                ", lineCount=" + lines.size() +
                ", length=" + fullText.length() +
                '}';
    }
}
