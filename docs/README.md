# 프로젝트 문서 (Documentation)

배치로그 분석 시스템(`batchLogAnalyze`)의 아키텍처, 마이그레이션 보고서, 정책 정의서 및 기술 가이드 문서 디렉터리입니다.

---

## 📑 문서 목록

1. [**Spring Boot 2.3.12 & JUnit 5 전환 작업 보고서 (SPRING_BOOT_JUNIT5_MIGRATION_REPORT.md)**](./SPRING_BOOT_JUNIT5_MIGRATION_REPORT.md)
   - Spring Boot 2.3.12 (Spring 5.2.22) 및 JUnit 5 (Jupiter 5.7.2) 전환 과정 상세
   - Gradle 8.x + Spring Boot 2.3.x 빌드 호환성 해결 전략
   - JUnit 4 → JUnit 5 코드 마이그레이션 매핑 테이블 및 단언문 변경 가이드
   - 150MB+ 대용량 로그 파일 메모리 OOM 해결 및 성능 최적화
   - 개발자 학습 및 실무 적용을 위한 단위/통합 테스트 지식 가이드 수록

2. [**배치로그 분석 정책 명세서 (로그분석정책.md)**](./%EB%A1%9C%EA%B7%B8%EB%B6%84%EC%84%9D%EC%A0%95%EC%B1%85.md)
   - 17개 배치 JOB (01~17)별 점검 항목, 정규식/검색 키워드 및 판정 기준 정의
