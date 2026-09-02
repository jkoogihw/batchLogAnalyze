# 프로젝트 문서 (Documentation)

배치로그 분석 시스템(`batchLogAnalyze`)의 아키텍처, 마이그레이션 보고서, 정책 정의서 및 객체지향 설계 학습 가이드 문서 디렉터리입니다.

---

## 📑 문서 목록

1. [**OOP 기반 리팩토링 및 SOLID 원칙 가이드 (OOP_REFACTORING_GUIDE.md)**](./OOP_REFACTORING_GUIDE.md)
   - 절차지향 `if-else` 분기 구조에서 객체지향(OOP) 구조로의 전환 과정 상세
   - SOLID 5대 원칙(SRP, OCP, LSP, ISP, DIP) 실무 적용 사례
   - 4대 디자인 패턴(전략 패턴, 팩토리/레지스트리 패턴, 퍼사드 패턴, 컴포지트 패턴) 분석
   - 100% 하위 호환성 유지 전략 및 개발자 셀프 체크리스트

2. [**Spring Boot 2.3.12 & JUnit 5 전환 작업 보고서 (SPRING_BOOT_JUNIT5_MIGRATION_REPORT.md)**](./SPRING_BOOT_JUNIT5_MIGRATION_REPORT.md)
   - Spring Boot 2.3.12 (Spring 5.2.22) 및 JUnit 5 (Jupiter 5.7.2) 전환 과정 상세
   - Gradle 8.x + Spring Boot 2.3.x 빌드 호환성 해결 전략
   - JUnit 4 → JUnit 5 코드 마이그레이션 매핑 테이블 및 단언문 변경 가이드
   - 150MB+ 대용량 로그 파일 메모리 OOM 해결 및 성능 최적화

3. [**배치로그 분석 정책 명세서 (로그분석정책.md)**](./%EB%A1%9C%EA%B7%B8%EB%B6%84%EC%84%9D%EC%A0%95%EC%B1%85.md)
   - 17개 배치 JOB (01~17)별 점검 항목, 정규식/검색 키워드 및 판정 기준 정의

4. [**내부망 환경 프로젝트 이관 및 Eclipse 설정 가이드 (내부망_환경_프로젝트_이관_및_설정_가이드.md)**](./%EB%82%B4%EB%B6%80%EB%A7%9D_%ED%99%98%EA%B2%BD_%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8_%EC%9D%B4%EA%B4%80_%EB%B0%8F_%EC%84%A4%EC%A0%95_%EA%B0%80%EC%9D%B4%EB%93%9C.md)
   - GitHub 소스 다운로드부터 로컬 `libs/` 추출, Gradle 오프라인/Wrapper 설정
   - Eclipse Buildship 및 Java Project Import, Gradle Tasks `application` 활성화
   - 내부망 환경 트러블슈팅 및 67개 테스트/실행 검증 체크리스트
