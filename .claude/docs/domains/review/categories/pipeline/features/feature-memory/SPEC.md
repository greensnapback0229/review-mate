# Feature Memory - SPEC (MVP, 구현 완료)

## 개요
Feature별 과거 리뷰에서 학습한 지식을 MySQL에 저장하고 조회하여,
리뷰 시 과거 맥락을 LLM에 제공하는 시스템.

## 상태: 구현 완료

## 관련 파일
- `FeatureMemoryRepository.java` - 저장/조회 로직 (RDB 기반)
- `FeatureMemoryJpaRepository.java` - JPA Repository 인터페이스
- `RepositoryJpaRepository.java` - Repository 엔티티 JPA
- `feature/entity/FeatureMemory.java` - JPA Entity
- `feature/entity/Repository.java` - JPA Entity
- `feature/dto/FeatureMemory.java` - 도메인 DTO
- `feature/dto/ResolvedFeature.java` - Definition + Memory 조합 DTO

## 범위 정의

### In-Scope
- Feature별 학습 내용 저장 (summary, keyPoints, relatedFiles)
- Repository + Feature 단위 격리
- LLM memorySuggestion 기반 메모리 업데이트
- 기존 메모리와 새 제안 병합

### Out-of-Scope
- 메모리 자동 정리/요약
- 메모리 버전 관리
- 사용자 수동 메모리 편집 API

## 의존성
- **의존**: MySQL (feature_memory 테이블)
- **피의존**: `PromptBuilder` → 프롬프트에 메모리 포함
- **피의존**: `ReviewAggregator` → 리뷰 후 메모리 업데이트

## DB 스키마

### repository 테이블
```sql
CREATE TABLE repository (
    repository_id BIGINT PRIMARY KEY,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### feature_memory 테이블
```sql
CREATE TABLE feature_memory (
    feature_memory_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    repository_id          BIGINT       NOT NULL,
    feature_name           VARCHAR(255) NOT NULL,
    feature_memory_content JSON         NOT NULL,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (repository_id) REFERENCES repository(repository_id),
    UNIQUE KEY uk_repo_feature (repository_id, feature_name)
);
```

## FeatureMemory DTO 구조
```java
@Getter @Builder
public class FeatureMemory {
    private String feature;       // Feature 식별자
    private String summary;       // 요약
    private List<String> keyPoints;    // 핵심 포인트
    private List<String> relatedFiles; // 관련 파일
    private LocalDateTime updatedAt;   // 업데이트 시각
}
```

## 동작

### 저장 (save)
```
1. repositoryId로 Repository 엔티티 존재 확인 → 없으면 생성
2. FeatureMemory DTO → JSON 직렬화
3. 기존 메모리 조회 (repositoryId + featureName)
4. 있으면 → 업데이트 (같은 PK로 save)
5. 없으면 → 새로 생성
```

### 조회 (findByFeature)
```
1. repositoryId + featureName으로 JPA 조회
2. entity.featureMemoryContent (JSON) → FeatureMemory DTO 역직렬화
3. Optional<FeatureMemory> 반환
```

### FeatureResolver
- `resolve(repositoryId, featureName, definition)` → `ResolvedFeature`
  - FeatureDefinition (정적 명세) + FeatureMemory (동적 지식) 조합
- `filterRelatedFiles(definition, changedFiles)` → 관련 파일 필터링
  - `definition.paths`의 prefix로 `startsWith` 매칭

## 메모리 업데이트 흐름
```
LLM 리뷰 응답 → memorySuggestion 포함
    ↓
ReviewAggregator.updateMemoryFromSuggestion()
    ↓
기존 메모리 조회
    ↓
있으면: summary 병합 (" | " 구분), keyPoints 병합, relatedFiles 병합 (중복 제거)
없으면: LLM 제안 그대로 저장
    ↓
FeatureMemoryRepository.save()
```

## 크기 및 제한

| 항목 | 현재 값 | 권장 상한 | 비고 |
|------|---------|-----------|------|
| summary 길이 | 제한 없음 (무한 연결) | **500자** | `" \| "` 연결로 무한 증가, 500자 초과 시 LLM 요약 필요 |
| keyPoints 수 | 제한 없음 (누적) | **10개** | 오래된 항목 자동 제거 (FIFO) 또는 LLM 정리 |
| relatedFiles 수 | 제한 없음 (중복 제거) | **20개** | 20개 초과 시 최근 변경 기준 정리 |
| Feature당 메모리 수 | 1개 (repositoryId + featureName 유니크) | 1개 | 정상 |
| Repository당 Feature 수 | 제한 없음 | **50개** | Feature Registry YAML 크기 제한 |

### 메모리 보존 정책 (미구현, 권장)

| 정책 | 설명 |
|------|------|
| 자동 정리 주기 | 30일마다 미사용 메모리 정리 |
| 미사용 기준 | 마지막 업데이트 후 90일 경과 |
| summary 압축 | 500자 초과 시 LLM으로 요약 후 대체 |
| keyPoints 정리 | 10개 초과 시 LLM으로 중복/구식 제거 |

## 에러 처리 정책

| 상황 | 동작 | 영향 |
|------|------|------|
| DB 연결 실패 | 예외 전파 → 리뷰 실패 | PR 리뷰 중단 |
| 메모리 조회 실패 (SELECT) | 빈 메모리로 계속 진행 | 메모리 없이 리뷰 수행 |
| 메모리 저장 실패 (INSERT/UPDATE) | 에러 로그 + 리뷰는 계속 | 메모리 업데이트 누락 |
| 데이터 무결성 위반 (중복 키) | UPSERT로 처리 | 기존 메모리 덮어쓰기 |
| summary/keyPoints null | 빈 문자열/리스트로 처리 | 정상 동작 |

## 테스트 현황
- `FeatureResolverTest.java` - resolve, filterRelatedFiles 테스트

## 알려진 제한
- 메모리 병합 시 summary가 `" | "` 구분으로 계속 길어짐 (최대 크기 제한 없음)
- keyPoints가 무한히 누적됨 (정리/요약 로직 없음)
- 역직렬화 실패 시 `null` 반환 (에러 무시)
