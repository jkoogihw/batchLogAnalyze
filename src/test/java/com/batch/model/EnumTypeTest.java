package com.batch.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================================
 * [단위 테스트 - 도메인 열거형(Enum) 규격화 검증]
 * -------------------------------------------------------------------------------------
 * 💡 학습 포인트:
 * 1. Enum 매핑 및 null-safe / 대소문자 무시(case-insensitive) 변환 검증:
 *    - 외부 입력(JSON, 설정, CLI) 문자열이 다양한 형태("search", "SEARCH", " Search ")로
 *      들어오더라도 정확한 타입으로 파싱되는지 검증합니다.
 * 2. 알 수 없는 입력에 대한 안전한 Fallback(UNKNOWN 또는 DAILY 기본값) 처리 검증.
 * =====================================================================================
 */
@DisplayName("단위 테스트: 도메인 상태값 Enum 규격화 및 변환 무결성")
public class EnumTypeTest {

    @Test
    @DisplayName("RuleType Enum 변환: 대소문자 무시, 공백 처리 및 UNKNOWN 폴백")
    public void testRuleType_FromString() {
        assertAll("RuleType 변환 단언",
            () -> assertEquals(RuleType.SEARCH, RuleType.fromString("SEARCH")),
            () -> assertEquals(RuleType.SEARCH, RuleType.fromString("search")),
            () -> assertEquals(RuleType.SEARCH, RuleType.fromString(" Search "), "공백 포함 처리"),
            () -> assertEquals(RuleType.DISPLAY, RuleType.fromString("DISPLAY")),
            () -> assertEquals(RuleType.STEP_METRICS, RuleType.fromString("STEP_METRICS")),
            () -> assertEquals(RuleType.DATE_CHECK, RuleType.fromString("DATE_CHECK")),
            () -> assertEquals(RuleType.HOLIDAY, RuleType.fromString("HOLIDAY")),
            () -> assertEquals(RuleType.UNKNOWN, RuleType.fromString("INVALID_TYPE"), "미정의 룰 UNKNOWN 반환"),
            () -> assertEquals(RuleType.UNKNOWN, RuleType.fromString(null), "null 입력 시 UNKNOWN 반환"),
            () -> assertEquals(RuleType.UNKNOWN, RuleType.fromString(""), "빈 문자열 입력 시 UNKNOWN 반환")
        );
    }

    @Test
    @DisplayName("ConditionType Enum 변환: 대소문자 무시 및 UNKNOWN 폴백")
    public void testConditionType_FromString() {
        assertAll("ConditionType 변환 단언",
            () -> assertEquals(ConditionType.COUNT_CHECK, ConditionType.fromString("COUNT_CHECK")),
            () -> assertEquals(ConditionType.COUNT_CHECK, ConditionType.fromString("count_check")),
            () -> assertEquals(ConditionType.EQUALS_0, ConditionType.fromString("EQUALS_0")),
            () -> assertEquals(ConditionType.EQUALS_N, ConditionType.fromString("EQUALS_N")),
            () -> assertEquals(ConditionType.ROLLBACK_ZERO, ConditionType.fromString("ROLLBACK_ZERO")),
            () -> assertEquals(ConditionType.ERROR_IF_PRESENT, ConditionType.fromString("ERROR_IF_PRESENT")),
            () -> assertEquals(ConditionType.UNKNOWN, ConditionType.fromString("INVALID"), "미정의 조건 UNKNOWN"),
            () -> assertEquals(ConditionType.UNKNOWN, ConditionType.fromString(null), "null 조건 UNKNOWN")
        );
    }

    @Test
    @DisplayName("ScheduleType Enum 변환: 기본값(DAILY) 및 MONTHLY 매핑")
    public void testScheduleType_FromString() {
        assertAll("ScheduleType 변환 단언",
            () -> assertEquals(ScheduleType.DAILY, ScheduleType.fromString("DAILY")),
            () -> assertEquals(ScheduleType.DAILY, ScheduleType.fromString("daily")),
            () -> assertEquals(ScheduleType.MONTHLY, ScheduleType.fromString("MONTHLY")),
            () -> assertEquals(ScheduleType.MONTHLY, ScheduleType.fromString("monthly")),
            () -> assertEquals(ScheduleType.DAILY, ScheduleType.fromString(null), "null 입력 시 기본값 DAILY"),
            () -> assertEquals(ScheduleType.DAILY, ScheduleType.fromString(""), "빈값 입력 시 기본값 DAILY"),
            () -> assertEquals("일", ScheduleType.DAILY.getLabel()),
            () -> assertEquals("월", ScheduleType.MONTHLY.getLabel())
        );
    }

    @Test
    @DisplayName("CheckStatus Enum 상태 및 성공/실패 판정 검증")
    public void testCheckStatus_IsSuccess() {
        assertAll("CheckStatus 판정 단언",
            () -> assertTrue(CheckStatus.PASS.isPassed()),
            () -> assertFalse(CheckStatus.PASS.isFailed()),
            () -> assertTrue(CheckStatus.HOLIDAY.isPassed(), "비영업일은 전체 통과로 간주"),
            () -> assertTrue(CheckStatus.SKIP.isPassed(), "점검제외는 전체 통과로 간주"),
            () -> assertFalse(CheckStatus.FAIL.isPassed()),
            () -> assertTrue(CheckStatus.FAIL.isFailed()),
            () -> assertTrue(CheckStatus.FILE_NOT_FOUND.isFailed()),
            () -> assertTrue(CheckStatus.ERROR.isFailed())
        );
    }
}
