# Batch Log Analyzer (배치로그 분석 프로그램)

정책 기반 배치 로그 자동 분석 및 검증 도구

## 프로젝트 구조

```
batchLogAnalyze/
├── src/
│   ├── main/
│   │   ├── java/com/batch/
│   │   │   └── CheckLog.java       # 메인 프로그램
│   │   └── resources/
│   │       ├── application.properties  # 프로젝트 설정
│   │       └── policy_meta.json       # 검증 정책 정의
│   └── test/java/                      # 테스트 코드 (향후)
├── build.gradle                    # Gradle 빌드 설정
├── settings.gradle                 # Gradle 프로젝트 설정
├── .gitignore                      # Git 무시 파일 설정
└── README.md                       # 이 파일
```

## 빌드 방법

### 1. 직접 컴파일 (권장)

```bash
javac -encoding UTF-8 src/main/java/com/batch/CheckLog.java -d build/classes/main
```

### 2. Gradle 사용 (Gradle 8.9+, Java 11)

```bash
gradle build
# 또는
./gradlew build
```

## 실행 방법

### 폴더 자동 선택 (최신 날짜)
```bash
java -cp build/classes/main com.batch.CheckLog
```

### 특정 폴더 지정
```bash
java -cp build/classes/main com.batch.CheckLog 240901
```

## 설정 파일

### application.properties

프로젝트의 주요 설정을 정의합니다:

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

**수정 시 주의사항:**
- `base.folder`: 배치 로그가 저장된 최상위 폴더 경로
- `log.analysis.dir`: 정책 파일과 리포트가 저장될 하위 디렉토리명
- `policy.meta.file`: 검증 정책 파일명 (기본값: policy_meta.json)

### policy_meta.json

각 JOB별 검증 규칙을 JSON 형식으로 정의합니다.
상세한 정책 작성 방법은 [로그분석정책.md](로그분석정책.md) 참조

## 주요 기능

- **정책 기반 검증**: JSON 형식의 정책 파일로 검증 규칙 정의
- **다양한 검증 타입**:
  - SEARCH: 전체 텍스트에서 패턴 건수 집계
  - DISPLAY: 키워드 뒤의 수치 추출
  - STEP_METRICS: Spring Batch Step 통계 검증
- **비영업일 예외 처리**: 휴일 패턴 인식 및 자동 판정
- **마크다운 리포트 생성**: 분석 결과를 마크다운 형식으로 저장
- **콘솔 결과 출력**: 즉시 결과 확인 가능

## 출력 결과

### 콘솔 출력
```
================================================================================
  [배치 로그 자동 분석 및 정상 여부 검증 프로그램 (CheckLog)]
================================================================================
>> 분석 대상 폴더: D:\job\hw\배치로그\240901 (240901)
>> 로드된 배치 정책 수: 17개 JOB
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
- **패키지**: `com.batch`

## 주요 클래스

- `CheckLog`: 메인 프로그램, 로그 분석 및 리포트 생성
- `JobPolicy`: 배치 JOB 검증 정책 모델
- `Rule`: 개별 검증 규칙 정의
- `CheckResult`: JOB 검증 결과
- `RuleResult`: 개별 규칙 검증 결과

## 정책 파일 필수 조건

정책 메타데이터 파일이 없으면 **프로그램이 즉시 종료**됩니다.

```
[오류] 정책 메타데이터 파일이 존재하지 않습니다
[안내] application.properties에서 경로 설정을 확인하세요
```

반드시 `src/main/resources/policy_meta.json` 파일을 준비하세요.

## 라이선스

Internal Use Only
