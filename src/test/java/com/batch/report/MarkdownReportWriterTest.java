package com.batch.report;

import com.batch.model.CheckResult;
import com.batch.model.JobPolicy;
import com.batch.model.RuleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("단위 테스트: MarkdownReportWriter 마크다운 표 2줄 서식 렌더링 검증")
class MarkdownReportWriterTest {

    @Test
    @DisplayName("JOB ID 2행(파일ID rawPattern) 및 JOB 이름 2행(스케줄 정보) 표 서식 검증")
    void testMarkdownReportWriter_TwoLineFormat(@TempDir Path tempDir) throws IOException {
        // [Given] 1. 일간 JOB 및 월간 JOB 생성
        JobPolicy p1 = JobPolicy.builder("04", "smrmJob102")
                .jobTitle("smrmJob102 102_조사대상결과반영")
                .rawPattern("_11401_")
                .daily("11:00")
                .build();
        CheckResult cr1 = new CheckResult(p1);
        cr1.addRuleResult(RuleResult.pass("01", "배치파일점검", "DISPLAY", "2026-09-01 11:00:02", "정상파일수집"));
        cr1.addRuleResult(RuleResult.pass("02", "건수확인", "DISPLAY", "10건", "정상"));

        JobPolicy p2 = JobPolicy.builder("18", "smpmJob206")
                .jobTitle("206_협회코드및보험사코드수집")
                .rawPattern("_11294_")
                .monthly(2, "00:45")
                .build();
        CheckResult cr2 = new CheckResult(p2);
        cr2.markAsMonthlyNotRun("18_smpmJob206_*.log (미생성)");
        cr2.addRuleResult(RuleResult.pass("01", "배치파일점검", "DISPLAY", "-", "월간배치 미실행일 (정상)"));

        List<CheckResult> results = new ArrayList<>();
        results.add(cr1);
        results.add(cr2);

        // [When] 마크다운 리포트 생성
        File reportFile = tempDir.resolve("test_report.md").toFile();
        MarkdownReportWriter writer = new MarkdownReportWriter(reportFile);
        File writtenFile = writer.write("test_folder", results, 2, 2, 0);

        // [Then]
        assertNotNull(writtenFile);
        assertTrue(writtenFile.exists());

        String content = Files.readString(writtenFile.toPath(), StandardCharsets.UTF_8);

        // 1. 헤더 검증
        assertTrue(content.contains("| 번호 | JOB ID | JOB 이름 | 점검항목 | 점검내용 | 점검결과 |"));

        // 2. JOB 04 검증: JOB ID에 _11401_, JOB 이름에 11:00 [전일 / 일] 포함 확인
        assertTrue(content.contains("smrmJob102<br/>_11401_"), "JOB ID 2행에 rawPattern(_11401_)이 표시되어야 함");
        assertTrue(content.contains("102_조사대상결과반영<br/>11:00 [전일 / 일]"), "JOB 이름 2행에 scheduleInfo가 표시되어야 함");

        // 3. JOB 18 (월간 배치 미생성) 검증: JOB ID에 _11294_, JOB 이름에 00:45 [2일 / 월], 점검내용에 월간배치 미실행일(정상)
        assertTrue(content.contains("smpmJob206<br/>_11294_"), "월간 JOB ID 2행에 rawPattern(_11294_)이 표시되어야 함");
        assertTrue(content.contains("206_협회코드및보험사코드수집<br/>00:45 [2일 / 월]"), "월간 JOB 이름 2행에 scheduleInfo가 표시되어야 함");
        assertTrue(content.contains("월간배치 미실행일 (정상)"), "월간 배치 미실행 상태 메시지가 표시되어야 함");
        assertTrue(content.contains("✅ 정상"), "월간 배치 미실행 건이 정상으로 처리되어야 함");
    }
}
