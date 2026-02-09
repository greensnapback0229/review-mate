# Code Collector - SPEC (MVP, 구현 완료)

## 개요
GitHub API를 통해 PR의 변경된 파일 diff와 핵심 파일의 전체 코드를 수집하는 컴포넌트.

## 상태: 구현 완료

## 관련 파일
- `CodeCollector.java` - 코드 수집 로직
- `collector/dto/CollectedCode.java` - 수집 결과 DTO
- `collector/dto/FileContent.java` - 파일 내용 DTO

## 범위 정의

### In-Scope
- PR 변경 파일의 diff 수집
- Feature 정의의 coreFiles 내용 수집
- 파일 경로 해석 (resolveFilePath)

### Out-of-Scope
- 파일 내용 캐싱
- 대용량 파일 분할 처리
- 바이너리 파일 처리

## 의존성
- **의존**: GitHub API (PR files, Repository content)
- **의존**: `FeatureRegistry` → coreFiles 경로 참조
- **피의존**: `PrReviewService` → 코드 수집 요청

## FileContent 구조
```java
@Getter @Builder
public class FileContent {
    private String path;      // 파일 경로
    private String content;   // 전체 내용 (core/additional)
    private String diff;      // diff 내용 (changed)
    private FileType type;    // CHANGED, CORE, ADDITIONAL
}
```

## CollectedCode 구조
```java
@Getter @Builder
public class CollectedCode {
    private List<FileContent> changedFiles;     // 변경된 파일 (diff)
    private List<FileContent> coreFiles;        // 핵심 파일 (전체 코드)
    private List<FileContent> additionalFiles;  // 추가 요청 파일
}
```

## 주요 메서드

### getChangedFilePaths(repoFullName, prNumber)
- PR의 모든 변경 파일 경로 반환
- `GHPullRequest.listFiles()` → `GHPullRequestFileDetail.getFilename()`

### collectChangedFiles(repoFullName, prNumber, filteredPaths)
- `filteredPaths`에 포함된 파일만 수집
- diff 내용은 `GHPullRequestFileDetail.getPatch()` 사용

### collectCoreFiles(repoFullName, branch, coreFilePaths, changedFilePaths)
- Feature Registry의 `coreFiles`에 정의된 파일의 전체 코드 수집
- 파일명만 있으면 changedFiles에서 전체 경로 매칭 (`resolveFilePath`)
- `GHContent.getContent()`로 Base64 디코딩된 내용 가져옴

### collectAdditionalFiles(repoFullName, branch, additionalFilePaths, changedFilePaths)
- 2차 리뷰용 추가 파일 수집 (현재 미사용, F2에서 활용 예정)
- 동작 방식은 `collectCoreFiles`와 동일

### collectAll(repoFullName, prNumber, branch, filteredPaths, coreFilePaths)
- 변경 파일 + 핵심 파일을 한번에 수집하여 `CollectedCode` 반환

## resolveFilePath 로직
```
입력: coreFilePath (예: "PaymentService.java" 또는 "src/main/java/.../PaymentService.java")
    ↓
경로에 "/" 포함? → 전체 경로로 판단 → 그대로 사용
    ↓
파일명만? → changedFilePaths에서 같은 파일명 검색 → 매칭된 전체 경로 사용
    ↓
매칭 실패 → 원래 경로 반환 (404 발생 예상)
```

## 크기 및 제한

| 항목 | 현재 값 | 권장 상한 | 비고 |
|------|---------|-----------|------|
| PR 변경 파일 수 | 제한 없음 | **50개** | GitHub API 기본 페이징 30개, 50+ 파일은 분할 리뷰 권장 |
| 단일 파일 diff 크기 | GitHub API 제한 (~1MB) | - | GitHub이 자동 제한 |
| Core files 수 | Feature 정의에 따름 | **5개/Feature** | 너무 많으면 프롬프트 비대 |
| Core file 최대 크기 | 제한 없음 | **500줄 (약 15KB)** | 500줄 초과 시 핵심 부분만 추출 권장 |
| 전체 수집 코드 크기 | 제한 없음 | **100KB** | 프롬프트 토큰 한계와 연동 |

### 대용량 PR 대응 (미구현, F1에서 구현 예정)

| PR 규모 | 파일 수 | 대응 전략 |
|---------|---------|-----------|
| 소형 | 1-10개 | 전체 diff + core files 제공 |
| 중형 | 11-30개 | Feature별 관련 파일만 제공, core files 축소 |
| 대형 | 31-50개 | diff 요약 모드, core files 생략 |
| 초대형 | 50개+ | 리뷰 거부 또는 분할 리뷰 안내 |

## 에러 처리 정책

| 상황 | 동작 | 영향 |
|------|------|------|
| GitHub API Rate Limit 초과 | 예외 전파 → 500 응답 | PR 리뷰 실패 |
| 파일 경로 resolve 실패 | 해당 파일 스킵 + 경고 로그 | 일부 파일 리뷰 누락 |
| 바이너리 파일 | diff 없음 → 자동 스킵 | 정상 (바이너리 리뷰 불필요) |
| 파일 크기 초과 (GitHub API 1MB 제한) | content null → 스킵 | 대용량 파일 리뷰 누락 |
| PR diff 조회 실패 | 예외 전파 → 500 응답 | PR 리뷰 실패 |
| core file 경로가 존재하지 않음 | FileNotFoundException → 해당 파일 스킵 | 핵심 파일 컨텍스트 누락 |

## 테스트 현황
- `CodeCollectorTest.java` - 기본 수집 테스트

## 알려진 제한
- GitHub API 호출이 파일 수만큼 발생 (N+1 문제)
- 대용량 파일에 대한 크기 제한 없음
- collectChangedFiles와 collectCoreFiles에서 GitHub 클라이언트를 각각 생성 (토큰 2번 발급)
