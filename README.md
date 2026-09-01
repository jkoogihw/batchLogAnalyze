# Batch Log Analyzer (배치로그 분석 프로그램)

정책 기반 배치 로그 자동 분석 및 검증 도구

**v1.1**: 역할별 클래스 분리 및 테스트 케이스 추가

## 프로젝트 구조

```
batchLogAnalyze/
├── src/
│   ├── main/
│   │   ├── java/com/batch/
│   │   │   ├── CheckLog.java           # 메인 프로그램 (Orchestration)
│   │   │   ├── config/
│   │   │   │   └── Config.java         # 설정 관리
│   │   │   ├── policy/
│   │   │   │   └── PolicyManager.java  # 정책 로드/파싱
│   │   │   ├── analyzer/
│   │   │   │   └── LogAnalyzer.java    # 로그 분석
│   │   │   ├── extract/
│   │   │   │   └── ValueExtractor.java # 값 추출
│   │   │   ├── report/
│   │   │   │   └── ReportGenerator.java# 리포트 생성
│   │   │   └── model/
│   │   │       ├── JobPolicy.java      # 정책 모델
│   │   │       ├── Rule.java           # 규칙 모델
│   │   │       ├── CheckResult.java    # 검증 결과
│   │   │       ├── RuleResult.java     # 규칙 결과
│   │   │       └── StepMetrics.java    # Step 통계
│   │   └── resources/
│   │       ├── application.properties  # 프로젝트 설정
│   │       └── policy_meta.json        # 검증 정책
│   └── test/
│       ├── java/com/batch/
│       │   ├── extract/
│       │   │   └── ValueExtractorTest.java
│       │   ├── policy/
│       │   │   └── PolicyManagerTest.java
│       │   ├── analyzer/
│       │   │   └── LogAnalyzerTest.java
│       │   └── model/
│       │       └── ModelTest.java
│       └── resources/
│           ├── application.properties       # 테스트 설정
│           ├── policy_meta_test.json        # 테스트 정책
│           └── sample_logs/                 # 샘플 로그 파일
│               ├── test_job001_240901.log
│               ├── test_job002_240901.log
│               ├── test_job003_240901.log
│               └── test_job004_240901.log
├── build.gradle                    # Gradle 빌드 설정
├── settings.gradle                 # Gradle 프로젝트 설정
├── .gitignore                      # Git 무시 파일
├── gradlew                         # Gradle Wrapper
├── policy_meta.json                # 프로덕션 정책 (원본)
└── README.md                       # 이 파일
```

## 역할별 클래스 설명

### 핵심 기능 클래스

| 클래스 | 역할 | 책임 |
|-------|------|------|
| **Config** | 설정 관리 | `application.properties` 로드, 환경 변수 관리 |
| **PolicyManager** | 정책 관리 | JSON 파일 로드, 정책 파싱, 규칙 생성 |
| **LogAnalyzer** | 분석 엔진 | 로그 파일 검증, 규칙 평가, 결과 생성 |
| **ValueExtractor** | 값 추출 | SEARCH/DISPLAY/STEP_METRICS 값 추출 |
| **ReportGenerator** | 리포트 생성 | 콘솔 출력, 마크다운 리포트 저장 |

### 데이터 모델 클래스

| 클래스 | 용도 |
|-------|------|
| **JobPolicy** | 각 JOB별 정책 정의 |
| **Rule** | 개별 검증 규칙 |
| **CheckResult** | JOB 검증 결과 |
| **RuleResult** | 규칙별 검증 결과 |
| **StepMetrics** | Spring Batch Step 통계 |

## 빌드 방법

### 1. 직접 컴파일 (권장)

```bash
javac -encoding UTF-8 src/main/java/com/batch/*.java src/main/java/com/batch/**/*.java -d build/classes/main
```

### 2. Gradle 사용 (Gradle 8.9+, Java 11)

```bash
gradle build
gradle test     # 테스트 실행
```

## 테스트 실행

### 모든 테스트 실행

```bash
gradle test
```

### 특정 테스트 클래스만 실행

```bash
gradle test --tests ValueExtractorTest
gradle test --tests PolicyManagerTest
gradle test --tests LogAnalyzerTest
gradle test --tests ModelTest
```

## 테스트 케이스

### ValueExtractorTest (19개 테스트 케이스)
- 단순 텍스트 매칭 건수 계산
- 정규식 패턴 매칭
- 값 추출 (콜론, 등호 포맷)
- 멀티라인 텍스트 처리
- 숫자 파싱 (콤마 포함)
- Step 메트릭 파싱

### PolicyManagerTest (11개 테스트 케이스)
- JSON 정책 파싱 (단일, 다중)
- 규칙 타입별 파싱 (SEARCH, DISPLAY, STEP_METRICS)
- 비영업일 설정 파싱
- Regex 필드 처리
- 여러 규칙 파싱

### LogAnalyzerTest (12개 테스트 케이스)
- SEARCH 규칙 평가 (EQUALS_0, EQUALS_N, 정규식)
- DISPLAY 규칙 평가 (EQUALS_0, ERROR_IF_PRESENT)
- STEP_METRICS 규칙 평가
- 비영업일 처리
- CheckResult 상태 관리

### ModelTest (10개 테스트 케이스)
- 모든 데이터 모델 객체 생성 및 상태 확인
- toString() 메서드 검증
- 상태 변경 추적

**총 52개 테스트 케이스**

## 실행 방법 및 파라미터 (logFileSrc)

프로그램 실행 시 `logFileSrc` 파라미터를 통해 분석 대상 로그 폴더를 유연하게 지정할 수 있습니다.

### 1. 직접 로그 폴더 지정 (Condition 1)
지정한 폴더 내에 `.log` 파일이 직접 존재하는 경우 해당 폴더를 대상으로 즉시 분석합니다.
```bash
java -cp build/classes/main com.batch.CheckLog src/test/resources/log_samples
# 또는 Named 파라미터
java -cp build/classes/main com.batch.CheckLog --logFileSrc=src/test/resources/log_samples
```

### 2. 상위 폴더 지정 시 최신 날짜 하위 폴더 자동 선택 (Condition 2)
지정한 폴더 내에 `.log` 파일이 없으면, 내부의 `260901` 같은 6자리 날짜 포맷(`\d{6}`) 하위 폴더 중 가장 최신 폴더를 자동으로 선택하여 분석합니다.
```bash
java -cp build/classes/main com.batch.CheckLog D:\job\hw\배치로그
```

### 3. 날짜 포맷(6자리 숫자) 직접 지정 (Condition 4)
`260901` 등 6자리 날짜 포맷을 입력하면 `application.properties`의 `base.folder` 하위 날짜 폴더(`base.folder/260901`)를 대상으로 분석합니다.
```bash
java -cp build/classes/main com.batch.CheckLog 260901
```

### 4. 로그 파일 미존재 시 실패 리포트 자동 생성 (Condition 3)
날짜 포맷 폴더가 없거나 최종 폴더에 `.log` 파일이 하나도 없는 경우, 분석 실패(FAIL) 내역이 담긴 마크다운 리포트 파일(`로그분석결과_{폴더명}.md`)을 자동으로 생성합니다.

### 5. 파라미터 미지정 시
기본 설정 경로(`base.folder`) 내의 최신 날짜 포맷(`\d{6}`) 폴더를 자동으로 탐색하여 분석합니다.
```bash
java -cp build/classes/main com.batch.CheckLog
```

## 설정 파일

### application.properties

```properties
# Base folder path for batch logs
base.folder=D:\\job\\hw\\배치로그

# Sub-directory for log analysis
log.analysis.dir=_로그분석

# Policy metadata file name
policy.meta.file=policy_meta.json

# Report file prefix
report.prefix=로그분석결과_

# Encoding
file.encoding=UTF-8
```

### policy_meta.json

각 JOB별 검증 규칙을 JSON 형식으로 정의합니다.

**예시:**
```json
[
  {
    "jobNo": "01",
    "jobName": "smrmJob001",
    "jobTitle": "SM RM Job001",
    "filePrefix": "smrm_",
    "holidayCheck": {
      "pattern": "(Saturday|Sunday)"
    },
    "rules": [
      {
        "type": "SEARCH",
        "target": "SUCCESS",
        "condition": "COUNT_CHECK",
        "description": "Success count"
      },
      {
        "type": "DISPLAY",
        "target": "Total Records",
        "condition": "EQUALS_N",
        "expectedCount": 100,
        "description": "Total records"
      }
    ]
  }
]
```

## 주요 기능

- **정책 기반 검증**: JSON 형식의 정책 파일로 검증 규칙 정의
- **다양한 검증 타입**:
  - SEARCH: 전체 텍스트에서 패턴 건수 집계
  - DISPLAY: 키워드 뒤의 수치 추출
  - STEP_METRICS: Spring Batch Step 통계 검증
- **비영업일 예외 처리**: 휴일 패턴 인식 및 자동 판정
- **마크다운 리포트 생성**: 분석 결과를 마크다운 형식으로 저장
- **콘솔 결과 출력**: 즉시 결과 확인 가능
- **역할별 클래스 분리**: 유지보수 및 테스트 용이
- **포괄적인 테스트**: 52개 테스트 케이스

## 출력 결과

### 콘솔 출력
```
================================================================================
  [배치 로그 자동 분석 및 정상 여부 검증 프로그램 (CheckLog)]
================================================================================
>> 분석 대상 폴더: D:\job\hw\배치로그\240901 (240901)
>> 로드된 배치 정책 수: 17개 JOB

=====================================================================
 [240901] 배치로그 분석 종합 결과 요약
=====================================================================
...
```

### 마크다운 리포트
- 파일: `{base.folder}/{log.analysis.dir}/로그분석결과_{폴더명}.md`
- 포함 내용:
  - 분석 일시 및 대상 폴더
  - JOB별 검증 결과 및 상세내용
  - 특이사항 및 참고 사항

## 기술 스택

- **언어**: Java 11
- **빌드 도구**: Gradle 8.9
- **테스트 프레임워크**: JUnit 4.13.2
- **패키지**: `com.batch.*`

## 아키텍처 특징

### 1. 역할별 분리 (Separation of Concerns)
각 클래스가 단일 책임만 수행하여 유지보수 용이

### 2. 의존성 주입 패턴
Config를 통한 중앙집중식 설정 관리

### 3. 테스트 용이성
- 개별 모듈을 독립적으로 테스트 가능
- 테스트 리소스(정책, 로그) 별도 준비
- Mock 없이 실제 데이터로 테스트

### 4. 확장성
- 새로운 규칙 타입 추가 용이
- 새로운 리포트 포맷 추가 용이
- 정책 파일만 수정하면 프로그램 재컴파일 불필요

## 정책 파일 필수 조건

정책 메타데이터 파일이 없으면 **프로그램이 즉시 종료**됩니다.

```
[오류] 정책 메타데이터 파일이 존재하지 않습니다
[안내] application.properties에서 경로 설정을 확인하세요
```

반드시 `src/main/resources/policy_meta.json` 파일을 준비하세요.

## 라이선스

Internal Use Only
