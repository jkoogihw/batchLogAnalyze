package com.batch.model;

/**
 * =====================================================================================
 * [개별 검증 규칙 모델 - Rule]
 * -------------------------------------------------------------------------------------
 * 💡 OOP 개선 포인트:
 * 1. Enum 기반 타입 안전성:
 *    - RuleType, ConditionType 지원으로 오타 및 런타임 오류 방지.
 * 2. Fluent Factory & Builder 패턴:
 *    - Rule.search(...), Rule.display(...), Rule.stepMetrics(...) 등의 정적 팩토리 메서드로
 *      테스트 및 규칙 정의 코드의 가독성을 대폭 향상.
 * 3. 100% 하위 호환성 유지:
 *    - 기존 String 필드 및 생성자를 온전히 유지하여 기존 JSON 파서/테스트 코드 변경 불필요.
 * =====================================================================================
 */
public class Rule {
    
    public String ruleNo;         // 규칙 번호 (예: "01", "02")
    public String type;           // DISPLAY, SEARCH, STEP_METRICS, DATE_CHECK, HOLIDAY
    public String target;         // 검색 대상 텍스트
    public String regex;          // 정규식 패턴
    public String stepName;       // Step 이름 (STEP_METRICS에서만 사용)
    public String condition;      // EQUALS_0, EQUALS_N, COUNT_CHECK, ROLLBACK_ZERO, ERROR_IF_PRESENT
    public int expectedCount = 0; // 기대값 (EQUALS_N에서만 사용)
    public String description;    // 규칙 설명

    public Rule() {
    }

    public Rule(String type, String target, String condition, String description) {
        this("", type, target, condition, description);
    }

    public Rule(String ruleNo, String type, String target, String condition, String description) {
        this.ruleNo = ruleNo;
        this.type = type;
        this.target = target;
        this.condition = condition;
        this.description = description;
    }

    public Rule(String ruleNo, RuleType ruleType, String target, ConditionType conditionType, String description) {
        this(ruleNo, ruleType != null ? ruleType.getCode() : "", target, conditionType != null ? conditionType.getCode() : "", description);
    }

    // =========================================================================
    // 팩토리 및 빌더 메서드 (Fluent API for Clean Code & Testability)
    // =========================================================================

    public static Rule search(String target, ConditionType condition) {
        return search(target, condition, target + " 건수확인");
    }

    public static Rule search(String target, ConditionType condition, String description) {
        return new Rule("", RuleType.SEARCH.getCode(), target, condition.getCode(), description);
    }

    public static Rule search(String target, ConditionType condition, int expectedCount, String description) {
        Rule r = new Rule("", RuleType.SEARCH.getCode(), target, condition.getCode(), description);
        r.expectedCount = expectedCount;
        return r;
    }

    public static Rule searchRegex(String regex, ConditionType condition, String description) {
        Rule r = new Rule("", RuleType.SEARCH.getCode(), "", condition.getCode(), description);
        r.regex = regex;
        return r;
    }

    public static Rule searchRegex(String regex, ConditionType condition, int expectedCount, String description) {
        Rule r = new Rule("", RuleType.SEARCH.getCode(), "", condition.getCode(), description);
        r.regex = regex;
        r.expectedCount = expectedCount;
        return r;
    }

    public static Rule display(String target, ConditionType condition) {
        return display(target, condition, target + " 수치확인");
    }

    public static Rule display(String target, ConditionType condition, String description) {
        return new Rule("", RuleType.DISPLAY.getCode(), target, condition.getCode(), description);
    }

    public static Rule display(String target, ConditionType condition, int expectedCount, String description) {
        Rule r = new Rule("", RuleType.DISPLAY.getCode(), target, condition.getCode(), description);
        r.expectedCount = expectedCount;
        return r;
    }

    public static Rule displayRegex(String regex, ConditionType condition, String description) {
        Rule r = new Rule("", RuleType.DISPLAY.getCode(), "", condition.getCode(), description);
        r.regex = regex;
        return r;
    }

    public static Rule stepMetrics(String stepName, ConditionType condition, String description) {
        Rule r = new Rule("", RuleType.STEP_METRICS.getCode(), stepName, condition.getCode(), description);
        r.stepName = stepName;
        return r;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(RuleType ruleType) {
        return new Builder().type(ruleType);
    }

    public static class Builder {
        private String ruleNo = "";
        private String type = "";
        private String target = "";
        private String regex = "";
        private String stepName = "";
        private String condition = "";
        private int expectedCount = 0;
        private String description = "";

        public Builder ruleNo(String ruleNo) {
            this.ruleNo = ruleNo;
            return this;
        }

        public Builder type(RuleType ruleType) {
            this.type = ruleType != null ? ruleType.getCode() : "";
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder regex(String regex) {
            this.regex = regex;
            return this;
        }

        public Builder stepName(String stepName) {
            this.stepName = stepName;
            return this;
        }

        public Builder condition(ConditionType condition) {
            this.condition = condition != null ? condition.getCode() : "";
            return this;
        }

        public Builder condition(String condition) {
            this.condition = condition;
            return this;
        }

        public Builder expectedCount(int expectedCount) {
            this.expectedCount = expectedCount;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Rule build() {
            Rule r = new Rule();
            r.ruleNo = this.ruleNo;
            r.type = this.type;
            r.target = this.target;
            r.regex = this.regex;
            r.stepName = this.stepName;
            r.condition = this.condition;
            r.expectedCount = this.expectedCount;
            r.description = this.description;
            return r;
        }
    }

    public RuleType getRuleType() {
        return RuleType.fromString(this.type);
    }

    public ConditionType getConditionType() {
        return ConditionType.fromString(this.condition);
    }

    @Override
    public String toString() {
        return "Rule{" +
                "ruleNo='" + ruleNo + '\'' +
                ", type='" + type + '\'' +
                ", description='" + description + '\'' +
                ", target='" + (target != null && target.length() > 20 ? target.substring(0, 20) + "..." : target) + '\'' +
                ", condition='" + condition + '\'' +
                '}';
    }
}
