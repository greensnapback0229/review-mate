# DECISION LOG - GitHub Review Client

---

## DL-01: Inline Comment 위치 지정 방식 (position vs line)

**날짜**: 2026-02-25
**상태**: 확정

### 컨텍스트

GitHub Pull Request inline comment를 작성할 때 두 가지 위치 지정 방식이 존재한다.

**발생 문제**: LLM이 전체 파일 코드(`addLineNumbers()`로 라인 번호를 붙여서 전달)를 보고 특정 라인을 지목하지만, 해당 라인이 diff hunk 범위 밖이면 comment가 누락되는 현상이 발생했다.

실제 발생 로그:
```
WARN : Cannot find diff position for ReviewService.java:104 - skipping inline comment
WARN : Cannot find diff position for ReviewService.java:79 - skipping inline comment
WARN : Cannot find diff position for ReviewService.java:137 - skipping inline comment
```

**원인**: GitHub diff는 변경된 라인과 주변 context(±3줄)만 포함하는 hunk 단위로 구성된다. 기존 구현은 diff patch를 파싱하여 `라인 번호 → diff position` 맵을 생성(`parsePatch()`)했는데, hunk에 포함되지 않은 라인은 이 맵에 존재하지 않아 comment를 달 수 없었다.

### 고려한 옵션

**A. 프롬프트 제약 (LLM에게 diff 범위 라인만 지목하도록 지시)**

diff에 포함된 라인 범위(hunk 목록)를 LLM에게 알려주고, 그 범위 내 라인에만 인라인 코멘트를 달도록 제약한다.

- 장점: 코드 변경 없이 프롬프트만 수정.
- 단점: LLM이 프롬프트 제약을 항상 준수하리라는 보장이 없다. 실제로 문제가 있는 라인이 hunk 밖에 있으면 유용한 코멘트가 누락된다. 리뷰 품질 저하 우려.

**B. 누락 코멘트를 general review에 포함**

position을 찾지 못한 comment를 버리지 않고 전체 리뷰 body에 `파일:라인 — 코멘트` 형태로 append한다.

- 장점: 구현이 간단하다. 기존 로직을 유지하면서 정보 손실만 방지한다.
- 단점: inline thread 형식이 아니라 가독성이 떨어진다. 코멘트가 파일/라인과 직접 연결되지 않아 개발자 경험이 나쁘다.

**C. kohsuke 라이브러리 업그레이드 + 신규 line-based API 사용 (채택)**

GitHub의 신규 Review Comment API는 diff position 대신 실제 파일 라인 번호(`line`)로 위치를 지정한다. kohsuke/github-api v1.330에서 `singleLineComment(body, path, line)` 메서드로 이를 지원한다.

- 장점: 라인 번호만 알면 파일의 어느 줄에나 코멘트를 달 수 있다. LLM이 전체 파일을 보고 지목한 모든 라인이 유효해진다. `parsePatch()` / `buildLineToPositionMap()` 로직 전체 제거 가능 → 코드 단순화 + API 호출 1회 감소.
- 단점: 라이브러리 업그레이드 필요 (1.319 → 1.330). 단, 마이너 버전 업이고 안정 버전이다.

### 조사 결과

- kohsuke v1.319: `comment(body, path, int position)` 만 존재. line-based 미지원.
- kohsuke v1.330 (최신 안정): `singleLineComment(body, path, int line)` 추가됨. ✅
- kohsuke v2.0-rc: line+side 지원 추가되었으나 아직 RC 단계 → 채택 보류.

### 결정: **C - kohsuke 1.330 업그레이드 + singleLineComment() 사용**

v1.330은 안정 릴리즈이고 마이너 버전 업이라 breaking change 위험이 낮다. `singleLineComment(body, path, line)`을 사용하면 diff hunk 제약 없이 파일의 모든 라인에 코멘트를 달 수 있어, LLM이 전달받은 전체 파일 코드(`addLineNumbers()`) 기준으로 반환한 라인 번호를 그대로 활용할 수 있다.

**변경 범위**:
- `build.gradle`: `github-api:1.319` → `github-api:1.330`
- `GitHubReviewClient`: `buildLineToPositionMap()`, `parsePatch()` 제거
- `GitHubReviewClient`: `reviewBuilder.comment(body, path, position)` → `reviewBuilder.singleLineComment(body, path, line)` 변경
- `PromptBuilder.addLineNumbers()`: 변경 없음 (LLM에게 라인 번호 전달 방식 동일)
- `InlineComment` DTO: 변경 없음 (side 필드 불필요)

### 재검토 조건

- multiline comment(여러 줄에 걸친 코멘트)가 필요해지면 `multiLineComment(body, path, startLine, endLine)` API도 v1.330에서 지원하므로 확장 가능.
- 삭제된 라인(old file 기준)에 코멘트가 필요한 케이스가 생기면 `side` 파라미터(LEFT/RIGHT) 지원을 위해 v2.0 stable 릴리즈 후 재검토.
