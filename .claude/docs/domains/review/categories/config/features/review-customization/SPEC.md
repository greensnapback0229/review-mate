# F6: Review Customization - SPEC

## 개요
Repository별로 리뷰 동작을 커스터마이징할 수 있는 설정 시스템을 구축한다.
`feature-registry.yml`을 확장하거나 별도 설정 파일로 관리한다.

## 현재 상태
- 모든 Repository에 동일한 리뷰 설정 적용 (시스템 프롬프트, 토큰 수, 언어 등)
- Feature Registry에 `reviewFocus`만 존재, 리뷰 스타일/심각도 기준 등 미지원

## 범위

### In-Scope
- Repository별 리뷰 설정 파일: `.github/pr-review/config.yml`
- 설정 가능 항목:
  - 리뷰 언어 (한국어/영어)
  - 리뷰 심각도 필터 (Critical만 / Critical+Major / 전부)
  - 최대 inline comments 수
  - 무시할 파일 패턴 (glob)
  - 커스텀 리뷰 지침 (추가 시스템 프롬프트)
- 설정 미존재 시 기본값 사용

### Out-of-Scope
- Web UI 기반 설정 관리
- DB 기반 설정 저장 (파일 기반만)
- Repository별 LLM 모델 선택 (F7에서 구현)

## 설정 파일 형식

```yaml
# .github/pr-review/config.yml
review:
  language: "ko"              # ko | en
  severity-filter: "all"      # critical | major | all
  max-inline-comments: 10
  max-tokens: 8000

  ignore-patterns:
    - "**/*.test.java"
    - "**/*.md"
    - "**/generated/**"

  custom-instructions: |
    - 변수명은 camelCase를 사용해야 합니다
    - 모든 public 메서드에 Javadoc이 필요합니다
    - Spring @Transactional 사용 시 주의사항을 확인하세요
```

## ReviewConfig DTO 정의

```java
@Getter @Builder
public class ReviewConfig {
    private String language;
    private String severityFilter;
    private int maxInlineComments;
    private int maxTokens;
    private List<String> ignorePatterns;
    private String customInstructions;
}
```

### 필드 상세

| 필드 | 타입 | YAML 키 | 기본값 | 유효 값 | 설명 |
|------|------|---------|--------|---------|------|
| `language` | String | `review.language` | `"ko"` | `"ko"`, `"en"` | 리뷰 언어 |
| `severityFilter` | String | `review.severity-filter` | `"all"` | `"critical"`, `"major"`, `"all"` | 보여줄 최소 심각도 |
| `maxInlineComments` | int | `review.max-inline-comments` | `10` | `1-50` | PR당 최대 inline 코멘트 수 |
| `maxTokens` | int | `review.max-tokens` | `8000` | `1000-16000` | LLM 응답 최대 토큰 |
| `ignorePatterns` | List\<String\> | `review.ignore-patterns` | `[]` (빈 리스트) | Ant glob 패턴 | 리뷰 제외 파일 패턴 |
| `customInstructions` | String | `review.custom-instructions` | `null` | 자유 텍스트 (최대 2000자) | 추가 리뷰 지침 |

### Validation 규칙

| 필드 | 규칙 | 위반 시 동작 |
|------|------|-------------|
| `language` | `"ko"` 또는 `"en"`만 허용 | 기본값 `"ko"` 사용 + 경고 로그 |
| `severityFilter` | `"critical"`, `"major"`, `"all"` 중 하나 | 기본값 `"all"` 사용 + 경고 로그 |
| `maxInlineComments` | 1 이상 50 이하 | 범위 밖이면 기본값 10 사용 |
| `maxTokens` | 1000 이상 16000 이하 | 범위 밖이면 기본값 8000 사용 |
| `ignorePatterns` | 유효한 Ant glob | 잘못된 패턴은 무시 + 경고 로그 |
| `customInstructions` | 2000자 이하 | 초과 시 2000자에서 자름 + 경고 로그 |

### 기본값 생성

```java
public static ReviewConfig defaults() {
    return ReviewConfig.builder()
        .language("ko")
        .severityFilter("all")
        .maxInlineComments(10)
        .maxTokens(8000)
        .ignorePatterns(List.of())
        .customInstructions(null)
        .build();
}
```
config.yml이 존재하지 않거나 파싱 실패 시 `ReviewConfig.defaults()`를 사용한다.

## 수정 대상 파일
- **신규**: `ReviewConfig.java` - 설정 DTO
- **신규**: `ReviewConfigLoader.java` - config.yml 로딩
- **수정**: `PromptBuilder.java` - 설정 기반 프롬프트 생성
- **수정**: `PrReviewService.java` - 설정 로딩 및 적용
- **수정**: `WebhookController.java` - ignore 패턴 필터링

## 테스트 케이스
1. config.yml 존재 → 설정 정상 로딩
2. config.yml 미존재 → 기본값 사용
3. language 설정에 따른 프롬프트 언어 변경
4. ignore-patterns으로 파일 필터링
5. max-inline-comments 제한 동작
6. custom-instructions가 시스템 프롬프트에 추가되는지

## 의존성
- Phase 1 완료 후 진행

## 완료 조건
- [ ] config.yml 로딩 구현
- [ ] 6개 이상 설정 항목 지원
- [ ] 기본값 fallback 동작
- [ ] 단위 테스트 5개 이상
