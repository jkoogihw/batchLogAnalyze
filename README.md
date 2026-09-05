# Batch Log Analyzer (배치로그 분석 프로그램)

정책 기반 배치 로그 자동 분석 및 일자/비즈니스 무결성 검증 도구

**v1.3**: 18개 배치 JOB 지원 (일간 17종 + 월간 1종), 일자 검증 엔진(`LogDateChecker`), 로그 경량화 유틸리티(`LogSlimmer`), Spring Boot 2.7.18 & JUnit 5 (85개 테스트 전수 통과)

---

## 📂 프로젝트 구조

```
batchLogAnalyze/
├── src/
│   ├── main/
│   │   ├── java/com/batch/
│   │   │   ├── BatchLogAnalyzerApplication.java # Spring Boot 메인 엔트리포인트
│   │   │   ├── CheckLog.java                    # CommandLineRunner 기반 CLI 실행 오케스트레이터
│   │   │   ├── analyzer/
│   │   │   │   ├── LogAnalyzer.java             # 로그 분석 총괄 엔진
│   │   │   │   ├── LogDateChecker.java          # [01] 배치파일점검 (시간/주기 일자 검증)
│   │   │   │   ├── HolidayChecker.java          # 비영업일(휴일) 판정 엔진
│   │   │   │   ├── LogFileLocator.java          # 로그 파일 패턴 매칭 및 탐색
│   │   │   │   └── evaluator/                   # 규칙 평가 전략 (Strategy Pattern)
│   │   │   │       ├── RuleEvaluator.java
│   │   │   │       ├── RuleEvaluatorRegistry.java
│   │   │   │       ├── SearchRuleEvaluator.java
│   │   │   │       ├── DisplayRuleEvaluator.java
│   │   │   │       └── StepMetricsRuleEvaluator.java
│   │   │   ├── config/
│   │   │   │   └── Config.java                  # application.properties 설정 관리
│   │   │   ├── extract/
│   │   │   │   └── ValueExtractor.java          # 수치/문자열/Step메트릭 정규식 추출기
│   │   │   ├── model/
│   │   │   │   ├── JobPolicy.java               # JOB 메타 및 스케줄 정의
│   │   │   │   ├── Rule.java                    # 개별 검증 규칙 모델 (ruleNo: "01"~)
│   │   │   │   ├── CheckResult.java             # JOB 종합 검증 결과 모델
│   │   │   │   ├── RuleResult.java              # 규칙별 검증 결과 모델
│   │   │   │   └── StepMetrics.java             # Spring Batch Step 통계 모델
│   │   │   ├── policy/
│   │   │   │   ├── PolicyManager.java           # JSON 정책 파싱 및 인메모리 관리
│   │   │   │   └── loader/                      # 정책 로더 (DIP 적용)
│   │   │   │       ├── PolicyLoader.java
│   │   │   │       ├── CompositePolicyLoader.java
│   │   │   │       ├── FilePolicyLoader.java
│   │   │   │       └── ClasspathPolicyLoader.java
│   │   │   ├── report/
│   │   │   │   ├── ReportGenerator.java         # 리포트 생성 파사드
│   │   │   │   ├── ReportWriter.java
│   │   │   │   ├── ConsoleReportWriter.java     # 콘솔 표 출력기
│   │   │   │   └── MarkdownReportWriter.java    # 마크다운 리포트 생성기
│   │   │   └── service/
│   │   │       └── BatchLogAnalysisService.java # 폴더 탐색 및 일괄 분석 비즈니스 서비스
│   │   └── resources/
│   │       ├── application.properties           # 기본 환경 설정 (기준시간, 폴더경로 등)
│   │       └── policy_meta.json                 # 18개 전체 JOB 검증 정책 메타데이터
│   └── test/
│       ├── java/com/batch/
│       │   ├── CheckLogTest.java                # CLI 인자 및 옵션 통합 테스트
│       │   ├── analyzer/
│       │   │   ├── LogAnalyzerTest.java
│       │   │   ├── LogDateCheckerTest.java      # 일자 검증 엔진 단위 테스트
│       │   │   └── LogSampleVerificationTest.java # 18개 전체 배치 실로그 전수 검증
│       │   ├── extract/
│       │   │   └── ValueExtractorTest.java
│       │   ├── model/
│       │   │   └── ModelTest.java
│       │   ├── policy/
│       │   │   └── PolicyManagerTest.java
│       │   └── util/
│       │       ├── LogSlimmer.java              # 테스트용 대용량 로그 슬림화 유틸리티
│       │       └── LogSlimmerTest.java          # LogSlimmer 단위 테스트
│       └── resources/
│           ├── application.properties           # 테스트 전용 설정
│           ├── policy_meta_test.json            # 테스트 전용 6개 JOB 축약 정책
│           ├── log_samples/                     # 17개 일간 배치 테스트용 샘플 로그
│           └── log_monthly/                     # 18개 전체(일간+월간) 경량화 샘플 로그
├── docs/                                        # 프로젝트 설계 및 아키텍처 문서
├── gradle/wrapper/                              # Gradle 8.9 Wrapper
├── libs/                                        # 오프라인/내부망 빌드용 JAR 라이브러리 (61종)
├── build.gradle                                 # Gradle 빌드 스크립트
├── settings.gradle                              # Gradle 프로젝트 설정
├── CheckLog.launch                              # Eclipse 실행용 Shared Launch 설정
├── CheckLogTest.launch                          # Eclipse 테스트용 Shared Launch 설정
└── README.md                                    # 본 문서
```

---

## 🌟 주요 기능 및 특징

1. **18개 배치 JOB 정책 기반 자동 점검**:
   - 일간 배치 17종 + 월간 배치 1종(`206_협회코드및보험사코드수집`) 검증 지원.
   - `policy_meta.json`으로 유지보수되며, 프로그램 재컴파일 없이 비즈니스 규칙 확장 가능.
2. **배치파일 일자 및 수집 정상성 자동 점검 (`LogDateChecker`)**:
   - `[01] 배치파일점검`: 로그 첫 행의 타임스탬프를 자동 파싱하여 기준일(`BaseDate`) 및 스케줄 시간(`09:05` 분기)에 맞는 당일/전일/월간 로그가 수집되었는지 자동 판정.
   - 정상 수집 시 `정상파일수집`으로 간결 표기.
3. **유연한 실행 옵션 (`--skipDateCheck` / `--allLogs`)**:
   - 일자 검증을 건너뛰고 폴더 내 모든 로그를 시간과 관계없이 즉시 분석 가능.
4. **객체지향 설계(SOLID) 및 디자인 패턴 적용**:
   - 전략 패턴(Strategy Pattern): `RuleEvaluator` (`SEARCH`, `DISPLAY`, `STEP_METRICS`)
   - 의존 역전 원칙(DIP): `PolicyLoader` (`Composite`, `File`, `Classpath`)
   - 단일 책임 원칙(SRP): `LogDateChecker`, `HolidayChecker`, `ReportWriter`
5. **테스트 영속성을 위한 로그 경량화 (`LogSlimmer`)**:
   - 수백 MB의 대용량 로그에서 핵심 검증 라인만 보존하여 99.8% 경량화(총 900KB)하고 Git에 완전 내장.

---

## ⚙️ 빌드 및 테스트 실행

### 1. 테스트 실행 (JUnit 5 - 85개 테스트 전수 통과)

```bash
# Windows PowerShell
$env:JAVA_HOME="D:\dev\bin\java\jdk-17.0.2"
.\gradlew test

# 특정 테스트 클래스만 실행
.\gradlew test --tests LogDateCheckerTest
.\gradlew test --tests LogSampleVerificationTest
.\gradlew test --tests LogSlimmerTest
```

### 2. 빌드 및 실행

```bash
# 빌드
.\gradlew build

# Gradle을 통한 실행
.\gradlew run --args="src/test/resources/log_monthly"

# 시간 무관 전체 처리 옵션 실행
.\gradlew run --args="--logFileSrc=src/test/resources/log_monthly --skipDateCheck"
```

---

## 📊 리포트 출력 예시

### 마크다운 리포트 표 서식

| 번호 | JOB ID | JOB 이름 | 점검항목 | 점검내용 | 점검결과 |
| :---: | :--- | :--- | :--- | :--- | :---: |
| 01 | gagastJob002<br/>_10702_ | 추천터치고객 통계 데이터 적립<br/>03:05 [당일 / 일] | **[01] 배치파일점검**<br/>`2026-09-02 03:05:02` | ✅ 정상파일수집 | ✅ 정상 |
| | | | **[02] DB Insert GA Count 0건 체크**<br/>`0` | ✅ 정상 (0건) | |
| 04 | smrmJob102<br/>_11401_ | 102_조사대상결과반영<br/>11:00 [전일 / 일] | **[01] 배치파일점검**<br/>`2026-09-01 11:00:02` | ✅ 정상파일수집 | ✅ 정상 |
| | | | **[04] UMS 발송결과 -> 리턴코드 200 검색 1건**<br/>`1건` | ✅ 정상 (1건 일치) | |
| 18 | smpmJob206<br/>_11294_ | 206_협회코드및보험사코드수집<br/>00:45 [2일 / 월] | **[01] 배치파일점검**<br/>`2026-09-02 00:45:03` | ✅ 정상파일수집 | ✅ 정상 |
| | | | **[02] HTTP/1.1 200 거래성공확인**<br/>`2건` | ✅ 건수확인: 2건 | |
| | | | **[03] prodList.size 이전데이터 정리 건수**<br/>`40` | ✅ 건수확인: 40 | |
| | | | **[04] TB_SMPM1002.insIntgCode 151건 실행 확인**<br/>`151건` | ✅ 정상 (151건 일치) | |

---

## 🛠️ 기술 스택

- **언어**: Java 17 (Java 11 호환)
- **프레임워크**: Spring Boot 2.7.18 (ApplicationRunner / CommandLineRunner)
- **빌드 도구**: Gradle 8.9 (Gradle Wrapper 포함)
- **테스트 프레임워크**: JUnit 5 (JUnit Jupiter 5.10.2)
- **IDE 지원**: Eclipse (Buildship, Java Project, Shared Launch 지원), IntelliJ IDEA, VS Code

---

## 📜 문서 변경 이력

| 작성/수정 일시 | 작업 내용 | 최종 수정 Commit |
| :--- | :--- | :--- |
| 2026-09-03 23:25:00 | 18개 배치 정책, LogDateChecker, LogSlimmer, 85개 JUnit 5 테스트 및 Spring Boot 2.7.18 구조 전면 현행화 | [`89bf863`](https://github.com/jkoogihw/batchLogAnalyze/commit/89bf863) |
