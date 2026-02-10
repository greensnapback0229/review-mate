# Feature Registry - SPEC (MVP, 구현 완료)

## 개요
리뷰 대상 Repository의 `.github/pr-review/feature-registry.yml`에서 Feature 정의를 로드하여,
코드 파일을 비즈니스 Feature 단위로 분류하는 시스템.

## 상태: 구현 완료

## 관련 파일
- `FeatureRegistry.java` - Feature 조회/관리 컴포넌트
- `FeatureRegistryLoader.java` - GitHub에서 YAML 로딩 + 파싱
- `FeatureDefinition.java` - Feature 정의 DTO

## 범위 정의

### In-Scope
- Repository별 `.github/pr-review/feature-registry.yml` 로딩
- Ant 패턴 기반 파일→Feature 매칭
- Feature 정의 (name, description, patterns, coreFiles, reviewFocus)
- 매칭되지 않는 파일은 UNKNOWN Feature로 분류

### Out-of-Scope
- Feature Registry UI 편집 (Web UI)
- Feature 자동 감지 (AI 기반)
- Feature간 의존성 정의

## 의존성
- **의존**: GitHub API (Repository 파일 조회)
- **피의존**: `PrReviewService` → Feature 분류에 사용
- **피의존**: `CodeCollector` → coreFiles 경로 참조

## Feature 정의 형식

```yaml
# .github/pr-review/feature-registry.yml
features:
  PAYMENT:
    description: "결제 및 금액 처리"
    paths:
      - "src/main/java/com/app/payment/"
    coreFiles:
      - "PaymentService.java"
  AUTH:
    description: "인증 및 권한 관리"
    paths:
      - "src/main/java/com/app/auth/"
      - "src/main/java/com/app/security/"
    coreFiles:
      - "AuthService.java"
```

### FeatureDefinition 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| `name` | String | Feature 식별자 (예: PAYMENT) |
| `description` | String | Feature 설명 |
| `paths` | List<String> | 관련 디렉토리 경로 (prefix 매칭) |
| `coreFiles` | List<String> | 핵심 파일 (변경 여부와 무관하게 LLM에 제공) |

## 동작

### FeatureRegistryLoader
```
1. GitHubAppAuthenticator로 Installation Token 발급
2. GitHub API로 feature-registry.yml 파일 읽기 (PR 브랜치 기준)
3. YAML 파싱 → Map<String, FeatureDefinition> 반환
```

### FeatureRegistry
- `initialize(repoFullName, token, branch)` → Loader 호출하여 registry Map 초기화
- `getFeature(name)` → Optional<FeatureDefinition>
- `getAllFeatures()` → List<FeatureDefinition>
- `hasFeature(name)` → boolean
- 초기화 안 된 상태에서 호출 시 `IllegalStateException` 발생

### 참고: PrReviewService에서는 Loader를 직접 사용
- 동시성 이슈로 `FeatureRegistry` 싱글턴 제거
- `featureRegistryLoader.loadFromRepository()` 직접 호출

## 에러 처리 정책

| 상황 | 동작 | 영향 |
|------|------|------|
| feature-registry.yml 미존재 | 빈 Feature 목록 반환 | 리뷰 생성되지 않음 |
| YAML 파싱 실패 (잘못된 형식) | 에러 로그 + 빈 목록 반환 | 리뷰 생성되지 않음 |
| Feature 패턴 매칭 실패 (매칭 Feature 없음) | UNKNOWN Feature로 분류 | 기본 리뷰 수행 |
| GitHub API 호출 실패 (파일 로딩) | 예외 전파 → 500 응답 | PR 리뷰 실패 |
| 잘못된 Ant 패턴 | PatternSyntaxException → 해당 Feature 스킵 | 일부 Feature 리뷰 누락 |

## 테스트 현황
- `FeatureRegistryTest.java` - 기본 매칭 테스트
- `FeatureRegistryLoaderTest.java` - YAML 로딩 테스트

## 알려진 제한
- `reviewFocus` 필드가 YAML에 정의 가능하지만 FeatureDefinition에 미포함 → F1에서 추가 예정
- 패턴 매칭이 `startsWith` 기반 (Ant 패턴/glob 미지원)
