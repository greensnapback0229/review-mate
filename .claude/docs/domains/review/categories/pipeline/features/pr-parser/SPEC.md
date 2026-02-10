# PR Parser - SPEC (MVP, 구현 완료)

## 개요
PR 제목, 본문에서 Feature 정보와 설명을 추출하여 PrContext 객체로 변환하는 파서.

## 상태: 구현 완료

## 관련 파일
- `PrParser.java` - 파싱 로직
- `parser/dto/PrContext.java` - 파싱 결과 DTO

## 범위 정의

### In-Scope
- PR 제목/본문에서 Feature 관련 정보 추출
- 정규식 기반 구조화된 PR 설명 파싱
- PrContext DTO 생성

### Out-of-Scope
- PR 템플릿 강제
- 자연어 기반 Feature 추론

## 의존성
- **의존**: WebhookPayload (PR 제목/본문)
- **피의존**: `PrReviewService` → PR 컨텍스트 추출

## PrContext 구조
```java
@Getter @Builder
public class PrContext {
    private String title;                // PR 제목
    private String summary;              // ## summary 섹션 내용
    private List<String> mainFeatures;   // main - PAYMENT, AUTH
    private List<String> relatedFeatures;// related - ALERT
    private List<String> description;    // ## description 섹션 항목들
    private List<String> changedFiles;   // 변경된 파일 경로 (외부 주입)
}
```

## 기대하는 PR 본문 형식

```markdown
## summary
- PR 내용 1-2줄 요약

main - PAYMENT, AUTH
related - ALERT

## description
- 디테일한 작업 내용1
- 작업시 고려사항 1
```

## 파싱 규칙

### extractSummary
- `## summary\n` 이후 ~ 다음 `##` 또는 끝까지
- 정규식: `## summary\s*\n(.+?)(?=\n##|$)` (DOTALL)

### extractMainFeatures
- `main - PAYMENT, AUTH` 형식
- 정규식: `main\s*-\s*(.+?)(?=\n|related|$)` (DOTALL)
- 쉼표(`,`)로 분리, trim 처리

### extractRelatedFeatures
- `related - ALERT, NOTIFICATION` 형식
- 정규식: `related\s*-\s*(.+?)(?=\n##|$)` (DOTALL)
- 쉼표(`,`)로 분리, trim 처리

### extractDescription
- `## description\n` 이후 `-`로 시작하는 라인만 수집
- `-` 기호 제거 후 내용만 List로 반환

## 테스트 현황
- `PrParserTest.java` - 기본 파싱 테스트

## 알려진 제한
- PR 본문 형식이 정해진 템플릿에 의존 (형식 벗어나면 빈 결과)
- `main -` / `related -` 이 본문 어디에나 있어도 매칭됨 (위치 제한 없음)
- Feature 이름 유효성 검증 없음 (Registry에 없는 Feature도 추출)
- `## check` 섹션은 파싱하지 않음
