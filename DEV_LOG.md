# PR Review Server - 전체 동작 원리 상세 설명

## 목차

1. [시스템 개요](https://www.notion.so/2026-1-23-2f1d8a098a4f806e8c32c3e1ce2e1c7b?pvs=21)
2. [전체 플로우](https://www.notion.so/2026-1-23-2f1d8a098a4f806e8c32c3e1ce2e1c7b?pvs=21)
3. [각 단계별 상세 설명](https://www.notion.so/2026-1-23-2f1d8a098a4f806e8c32c3e1ce2e1c7b?pvs=21)
4. [핵심 개념](https://www.notion.so/2026-1-23-2f1d8a098a4f806e8c32c3e1ce2e1c7b?pvs=21)
5. [코드 예시](https://www.notion.so/2026-1-23-2f1d8a098a4f806e8c32c3e1ce2e1c7b?pvs=21)

---

## 시스템 개요

### 목적

GitHub Pull Request의 코드 변경사항을 자동으로 분석하여, 기능(Feature) 단위로 지능적인 코드 리뷰를 제공하는 시스템입니다.

### 핵심 특징

- **Feature 기반 리뷰**: 코드를 기능 단위로 분류하여 맥락있는 리뷰 제공
- **Feature Memory**: 각 기능의 진화 과정을 기억하여 일관성 있는 리뷰
- **Inline Comments**: 특정 코드 라인에 직접 코멘트 (GitHub의 Review 기능)
- **LLM 기반**: Claude API를 사용한 고품질 코드 분석

---

## 전체 플로우

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          1. GitHub Event 발생                            │
│                                                                           │
│  Developer → git push → GitHub → PR 생성/업데이트 → Webhook 발송         │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      2. Webhook 수신 및 검증                             │
│                                                                           │
│  WebhookController.handlePullRequestEvent()                              │
│  - PR 이벤트 타입 확인 (opened/synchronize/reopened)                     │
│  - Draft PR 필터링                                                        │
│  - 기본 정보 추출 (repo, PR number, branch)                              │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    3. Feature Registry 로딩                              │
│                                                                           │
│  FeatureRegistry.initialize()                                            │
│  - .github/pr-review/feature-registry.yml 파일 읽기                      │
│  - Feature 정의 파싱:                                                     │
│    • Feature 이름 (예: USER_AUTHENTICATION)                              │
│    • 설명                                                                 │
│    • 관련 파일 경로 패턴 (paths)                                          │
│    • 핵심 파일 (coreFiles)                                                │
│    • 리뷰 포커스 (reviewFocus)                                            │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   4. 코드 변경사항 수집                                   │
│                                                                           │
│  CodeCollector.collectChangedCode()                                      │
│  - GitHub API로 PR의 변경된 파일 목록 가져오기                            │
│  - 각 파일의 변경 내용(diff) 수집                                         │
│  - 파일 내용 전체 읽기 (라인 번호 추가)                                   │
│                                                                           │
│  결과: Map<String, String>                                                │
│  {                                                                        │
│    "src/User.java": "1: package com.example;\n2: public class User...",  │
│    "src/Auth.java": "1: package com.example;\n2: public class Auth..."   │
│  }                                                                        │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   5. Feature별 코드 매칭                                  │
│                                                                           │
│  FeatureRegistry.getMatchingFeature()                                    │
│  - 각 변경된 파일을 Feature의 path 패턴과 매칭                            │
│  - 예: "src/auth/Login.java" → USER_AUTHENTICATION Feature               │
│                                                                           │
│  결과: Map<Feature, List<String>>                                         │
│  {                                                                        │
│    USER_AUTHENTICATION: ["src/auth/Login.java", "src/auth/Session.java"],│
│    PAYMENT_PROCESSING: ["src/payment/Card.java"]                         │
│  }                                                                        │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                  6. Feature Memory 조회 (MySQL)                          │
│                                                                           │
│  FeatureMemoryRepository.findByRepositoryIdAndFeatureName()              │
│  - 각 Feature의 과거 메모리 조회                                          │
│  - Feature Memory 내용:                                                   │
│    • 이 기능의 목적과 역할                                                │
│    • 핵심 설계 패턴                                                       │
│    • 주의해야 할 사항                                                     │
│    • 과거 리뷰에서 지적된 개선 사항                                       │
│                                                                           │
│  Feature Memory 예시:                                                     │
│  "USER_AUTHENTICATION 기능은 Spring Security를 사용하며,                 │
│   JWT 토큰 기반 인증을 구현합니다. 이전 리뷰에서 토큰 만료 시간이        │
│   너무 길다고 지적되었으므로, 새로운 변경사항에서 보안을 강화해야 합니다."│
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    7. LLM 프롬프트 생성                                   │
│                                                                           │
│  PromptBuilder.buildPrompt()                                             │
│  - Feature 정보 + Feature Memory + 변경된 코드를 하나의 프롬프트로 결합  │
│                                                                           │
│  프롬프트 구조:                                                           │
│  ┌─────────────────────────────────────┐                                 │
│  │ 1. Feature 설명                     │                                 │
│  │    - 이름, 목적, 범위               │                                 │
│  │                                     │                                 │
│  │ 2. Feature Memory (과거 맥락)       │                                 │
│  │    - 이전 리뷰 내용                 │                                 │
│  │    - 설계 결정 사항                 │                                 │
│  │                                     │                                 │
│  │ 3. Review Focus (리뷰 포인트)       │                                 │
│  │    - "보안 취약점 확인"             │                                 │
│  │    - "성능 최적화"                  │                                 │
│  │                                     │                                 │
│  │ 4. 변경된 코드 (라인 번호 포함)     │                                 │
│  │    src/auth/Login.java:             │                                 │
│  │    1: package com.example;          │                                 │
│  │    2: public class Login {          │                                 │
│  │    3:   public void login() {       │                                 │
│  │    ...                              │                                 │
│  │                                     │                                 │
│  │ 5. 응답 형식 지정 (JSON)            │                                 │
│  │    - generalReview (전체 리뷰)      │                                 │
│  │    - inlineComments (라인별 코멘트) │                                 │
│  │    - updatedMemory (메모리 업데이트)│                                 │
│  └─────────────────────────────────────┘                                 │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    8. Claude API 호출                                    │
│                                                                           │
│  LlmClient.reviewCode()                                                  │
│  - Claude API (claude-3-5-sonnet) 호출                                   │
│  - 응답 형식: JSON                                                        │
│                                                                           │
│  LLM 응답 예시:                                                           │
│  {                                                                        │
│    "generalReview": "전체적으로 인증 로직이 잘 구현되었습니다...",        │
│    "inlineComments": [                                                    │
│      {                                                                    │
│        "path": "src/auth/Login.java",                                    │
│        "line": 15,                                                        │
│        "body": "⚠️ 패스워드 검증 전에 입력값 sanitization 필요합니다"    │
│      },                                                                   │
│      {                                                                    │
│        "path": "src/auth/Session.java",                                  │
│        "line": 8,                                                         │
│        "body": "💡 세션 타임아웃을 환경변수로 관리하는 것을 권장합니다"  │
│      }                                                                    │
│    ],                                                                     │
│    "updatedMemory": "JWT 토큰 만료 시간이 1시간으로 개선되었습니다..."   │
│  }                                                                        │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                9. Feature Memory 업데이트 (MySQL)                        │
│                                                                           │
│  ReviewAggregator.aggregateReviews()                                     │
│  - LLM이 제안한 updatedMemory를 데이터베이스에 저장                       │
│  - 다음 리뷰 시 이 메모리를 활용하여 일관성 유지                          │
│                                                                           │
│  예시:                                                                    │
│  "USER_AUTHENTICATION 기능은 Spring Security + JWT를 사용합니다.         │
│   2024-01-20 리뷰: 토큰 만료 시간을 24시간 → 1시간으로 개선.             │
│   2024-01-23 리뷰: 입력값 sanitization 추가됨."                          │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               10. GitHub Review API로 리뷰 게시                          │
│                                                                           │
│  GitHubReviewClient.createReview()                                       │
│                                                                           │
│  Step 1: Diff 파싱 (Position 계산)                                       │
│  ───────────────────────────────────────                                 │
│  PR의 diff를 가져와서 파일별로 파싱:                                      │
│                                                                           │
│  Diff 예시:                                                               │
│  @@ -10,3 +10,5 @@ class Login {                                           │
│   10:  private String username;     (position: 1, context line)          │
│   11:  private String password;     (position: 2, context line)          │
│  +12:  private boolean isActive;    (position: 3, added line) ← 여기!    │
│  +13:  private Date lastLogin;      (position: 4, added line)            │
│   14:                                                                     │
│   15:  public void login() {        (position: 6, context line)          │
│                                                                           │
│  라인 번호 → Position 매핑:                                               │
│  {                                                                        │
│    10: 1,  // context line                                               │
│    11: 2,  // context line                                               │
│    12: 3,  // added line (LLM이 이 라인에 코멘트 달 수 있음)             │
│    13: 4,  // added line                                                 │
│    15: 6   // context line                                               │
│  }                                                                        │
│                                                                           │
│  Step 2: Review 객체 생성                                                 │
│  ───────────────────────────────────────                                 │
│  GHPullRequestReviewBuilder reviewBuilder = pullRequest.createReview();  │
│  reviewBuilder.body("전체적으로 좋습니다...");  // 전반적 리뷰             │
│                                                                           │
│  Step 3: Inline Comments 추가                                             │
│  ───────────────────────────────────────                                 │
│  for (InlineComment comment : inlineComments) {                          │
│    int line = comment.getLine();  // 15 (LLM이 반환한 라인 번호)         │
│    int position = lineToPositionMap.get(line);  // 3 (diff의 position)   │
│    reviewBuilder.comment(                                                │
│      comment.getBody(),     // "⚠️ 입력값 검증 필요"                      │
│      comment.getPath(),     // "src/auth/Login.java"                     │
│      position               // 3 (diff에서의 위치)                        │
│    );                                                                     │
│  }                                                                        │
│                                                                           │
│  Step 4: Review 제출                                                      │
│  ───────────────────────────────────────                                 │
│  reviewBuilder.event(GHPullRequestReviewEvent.COMMENT);  // 즉시 게시    │
│  GHPullRequestReview review = reviewBuilder.create();                    │
│                                                                           │
│  결과:                                                                    │
│  - Conversation 탭: 전체 리뷰 코멘트 표시                                 │
│  - Files Changed 탭: 각 라인에 inline comment 표시                        │
│    12: + private boolean isActive;  💬 "⚠️ 입력값 검증 필요"             │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         11. 완료 및 알림                                 │
│                                                                           │
│  - GitHub PR에 리뷰가 등록됨                                              │
│  - 개발자에게 알림 전송 (GitHub 기본 기능)                                │
│  - 로그 기록: "Successfully created review #12345"                        │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 각 단계별 상세 설명

### 1. GitHub Webhook 수신

**파일:** `WebhookController.java`

```java

@PostMapping("/api/webhook/pr")
public ResponseEntity<String> handlePullRequestEvent(
	@RequestBody String payload,
	@RequestHeader("X-GitHub-Event") String event
) {
	// 1. 이벤트 타입 확인
	if (!"pull_request".equals(event)) {
		return ResponseEntity.ok("Not a PR event");
	}

	// 2. JSON 파싱
	JsonNode rootNode = objectMapper.readTree(payload);
	String action = rootNode.get("action").asText();

	// 3. 처리할 액션만 필터링
	if (!List.of("opened", "synchronize", "reopened").contains(action)) {
		return ResponseEntity.ok("Ignored action: " + action);
	}

	// 4. PR 정보 추출
	JsonNode prNode = rootNode.get("pull_request");
	boolean isDraft = prNode.get("draft").asBoolean();
	if (isDraft) {
		return ResponseEntity.ok("Draft PR ignored");
	}

	int prNumber = prNode.get("number").asInt();
	String repoFullName = rootNode.get("repository").get("full_name").asText();

	// 5. 리뷰 프로세스 시작
	prReviewService.processPullRequest(repoFullName, prNumber);

	return ResponseEntity.ok("Review started");
}
```

**처리하는 이벤트:**

- `opened`: 새 PR 생성
- `synchronize`: PR에 새 커밋 푸시
- `reopened`: 닫힌 PR 재오픈

**무시하는 경우:**

- Draft PR
- `closed`, `labeled` 등 다른 액션

---

### 2. Feature Registry 로딩

**파일:** `FeatureRegistry.java`

Feature Registry는 **코드를 기능 단위로 분류하는 규칙**을 정의합니다.

**설정 파일:** `.github/pr-review/feature-registry.yml`

```yaml
features:
  - name: USER_AUTHENTICATION
    description: "사용자 인증 및 권한 관리 기능"
    paths:
      - "src/auth/**"
      - "src/security/**"
    coreFiles:
      - "src/auth/AuthService.java"
      - "src/security/SecurityConfig.java"
    reviewFocus:
      - "보안 취약점 확인 (SQL injection, XSS 등)"
      - "세션 관리의 적절성"
      - "패스워드 암호화 및 저장"

  - name: PAYMENT_PROCESSING
    description: "결제 처리 및 트랜잭션 관리"
    paths:
      - "src/payment/**"
    coreFiles:
      - "src/payment/PaymentService.java"
    reviewFocus:
      - "트랜잭션 일관성"
      - "환불 로직의 정확성"
      - "PCI DSS 준수"
```

**로딩 과정:**

```java
public void initialize(long repositoryId, String defaultBranch) {
	// 1. GitHub API로 feature-registry.yml 파일 가져오기
	GHRepository repo = github.getRepositoryById(repositoryId);
	GHContent content = repo.getFileContent(
		".github/pr-review/feature-registry.yml",
		defaultBranch
	);

	// 2. YAML 파싱
	String yaml = content.read().readAllBytes();
	FeatureRegistryConfig config = yamlMapper.readValue(yaml, FeatureRegistryConfig.class);

	// 3. 메모리에 캐싱
	for (FeatureConfig feature : config.getFeatures()) {
		PathMatcher matcher = new AntPathMatcher();
		features.put(feature.getName(), new Feature(feature, matcher));
	}
}
```

**핵심 역할:**

- 파일 경로 → Feature 매칭
- Feature별 리뷰 포커스 제공
- Core files를 통한 전체 맥락 제공

---

### 3. 코드 수집 및 Feature 매칭

**파일:** `CodeCollector.java`

```java
public Map<String, String> collectChangedCode(String repoFullName, int prNumber) {
	Map<String, String> codeMap = new HashMap<>();

	// 1. PR의 변경된 파일 목록 가져오기
	GHPullRequest pr = github.getRepository(repoFullName).getPullRequest(prNumber);
	PagedIterable<GHPullRequestFileDetail> files = pr.listFiles();

	// 2. 각 파일의 전체 내용 읽기
	for (GHPullRequestFileDetail file : files) {
		String filename = file.getFilename();

		// 3. 파일 내용 가져오기 (HEAD 브랜치)
		GHContent content = repo.getFileContent(filename, pr.getHead().getSha());
		String fileContent = new String(content.read().readAllBytes());

		// 4. 라인 번호 추가
		String[] lines = fileContent.split("\n");
		StringBuilder numbered = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			numbered.append(i + 1).append(": ").append(lines[i]).append("\n");
		}

		codeMap.put(filename, numbered.toString());
	}

	return codeMap;
}
```

**Feature 매칭 과정:**

```java
public Feature getMatchingFeature(String filepath) {
	for (Feature feature : features.values()) {
		for (String pathPattern : feature.getPaths()) {
			// Ant 스타일 패턴 매칭
			// "src/auth/**" matches "src/auth/Login.java"
			if (pathMatcher.match(pathPattern, filepath)) {
				return feature;
			}
		}
	}
	return null;  // 매칭되는 Feature 없음
}
```

---

### 4. LLM 프롬프트 생성

**파일:** `PromptBuilder.java`

```java
public String buildPrompt(
	Feature feature,
	String featureMemory,
	Map<String, String> changedFiles
) {
	StringBuilder prompt = new StringBuilder();

	// 1. Feature 설명
	prompt.append("# Feature: ").append(feature.getName()).append("\n\n");
	prompt.append("## Description\n");
	prompt.append(feature.getDescription()).append("\n\n");

	// 2. Feature Memory (과거 맥락)
	if (featureMemory != null && !featureMemory.isEmpty()) {
		prompt.append("## Feature Memory (Past Context)\n");
		prompt.append(featureMemory).append("\n\n");
	}

	// 3. Review Focus
	prompt.append("## Review Focus Points\n");
	for (String focus : feature.getReviewFocus()) {
		prompt.append("- ").append(focus).append("\n");
	}
	prompt.append("\n");

	// 4. Changed Code (라인 번호 포함)
	prompt.append("## Changed Code\n\n");
	for (Map.Entry<String, String> entry : changedFiles.entrySet()) {
		prompt.append("### ").append(entry.getKey()).append("\n");
		prompt.append("```java\n");
		prompt.append(entry.getValue());  // 라인 번호 포함된 코드
		prompt.append("```\n\n");
	}

	// 5. 응답 형식 지정
	prompt.append("## Response Format\n");
	prompt.append("Respond in JSON format:\n");
	prompt.append("{\n");
	prompt.append("  \"generalReview\": \"전반적인 리뷰...\",\n");
	prompt.append("  \"inlineComments\": [\n");
	prompt.append("    {\n");
	prompt.append("      \"path\": \"파일 경로\",\n");
	prompt.append("      \"line\": 라인번호,\n");
	prompt.append("      \"body\": \"코멘트 내용\"\n");
	prompt.append("    }\n");
	prompt.append("  ],\n");
	prompt.append("  \"updatedMemory\": \"업데이트된 메모리...\"\n");
	prompt.append("}\n");

	return prompt.toString();
}
```

**프롬프트 예시:**

```
# Feature: USER_AUTHENTICATION

## Description
사용자 인증 및 권한 관리 기능

## Feature Memory (Past Context)
이 기능은 Spring Security를 사용하며, JWT 토큰 기반 인증을 구현합니다.
2024-01-20 리뷰: 토큰 만료 시간이 24시간으로 설정되어 있어 보안상 위험합니다.
권장 사항: 만료 시간을 1시간으로 단축하고, refresh token 메커니즘을 도입하세요.

## Review Focus Points
- 보안 취약점 확인 (SQL injection, XSS 등)
- 세션 관리의 적절성
- 패스워드 암호화 및 저장

## Changed Code

### src/auth/Login.java
```

1: package com.example.auth;

2:

3: public class Login {

4:     private String username;

5:     private String password;

6:

7:     public boolean authenticate() {

8:         // TODO: Add password validation

9:         return true;

10:     }

11: }

```

## Response Format
Respond in JSON format:
{
  "generalReview": "전반적인 리뷰...",
  "inlineComments": [
    {
      "path": "src/auth/Login.java",
      "line": 8,
      "body": "⚠️ 패스워드 검증 로직이 누락되었습니다"
    }
  ],
  "updatedMemory": "2024-01-23 리뷰: authenticate() 메서드에 TODO가 있어 보안 위험..."
}
```

---

### 5. LLM 응답 파싱

**파일:** `LlmClient.java`

```java
public ReviewResponse reviewCode(String prompt) {
	// 1. Claude API 호출
	String apiResponse = callClaudeAPI(prompt);

	// 2. JSON 파싱
	JsonNode root = objectMapper.readTree(apiResponse);

	// 3. DTO로 변환
	ReviewResponse response = new ReviewResponse();
	response.setGeneralReview(root.get("generalReview").asText());

	// 4. Inline comments 파싱
	JsonNode commentsNode = root.get("inlineComments");
	List<InlineComment> comments = new ArrayList<>();
	for (JsonNode node : commentsNode) {
		InlineComment comment = new InlineComment();
		comment.setPath(node.get("path").asText());
		comment.setLine(node.get("line").asInt());
		comment.setBody(node.get("body").asText());
		comments.add(comment);
	}
	response.setInlineComments(comments);

	// 5. Updated memory
	response.setUpdatedMemory(root.get("updatedMemory").asText());

	return response;
}
```

---

### 6. GitHub Review 게시 (핵심!)

**파일:** `GitHubReviewClient.java`

이 부분이 **가장 복잡하고 중요한** 부분입니다.

#### 6.1. Position 계산의 필요성

**문제:** LLM은 **파일의 절대 라인 번호**를 반환하지만, GitHub Review API는 **diff의 상대 위치(position)**를 요구합니다.

**예시:**

원본 파일:

```java
1:package com.example;
2:
	3:

public class User {
4:
	private String name;
5:
	private int age;
6:
	7:

	public void setName(String name) {
		8:this.name = name;
		9:}
10:
}
```

PR에서 변경된 부분 (diff):

```diff
@@ -3,8 +3,10 @@ public class User {
 3:     private String name;
 4:     private int age;
+5:     private String email;  ← 새로 추가!
+6:     private boolean active;  ← 새로 추가!
 7: 
 8:     public void setName(String name) {
 9:         this.name = name;
```

**Diff의 position 계산:**

```
Position 1: @@ -3,8 +3,10 @@ (hunk header)
Position 2: Line 3 (context: private String name;)
Position 3: Line 4 (context: private int age;)
Position 4: Line 5 (added: private String email;)  ← 여기에 코멘트!
Position 5: Line 6 (added: private boolean active;)
Position 6: 빈 줄
Position 7: Line 8 (context: public void setName...)
Position 8: Line 9 (context: this.name = name;)
```

**LLM 응답:**

```json
{
  "path": "src/User.java",
  "line": 5,
  ←
  파일의
  5번
  라인
  "body": "⚠️ email 필드에 validation 필요"
}
```

**변환 필요:**

- 라인 5 → Position 4로 변환해야 GitHub API가 올바르게 처리

#### 6.2. Diff 파싱 알고리즘

```java
private Map<Integer, Integer> parsePatch(String patch) {
	Map<Integer, Integer> lineToPosition = new HashMap<>();

	String[] lines = patch.split("\n");
	int position = 0;         // diff에서의 위치 (0부터 시작)
	int currentLine = 0;      // 파일에서의 현재 라인

	// Hunk header 파싱: @@ -10,3 +10,5 @@
	Pattern hunkPattern = Pattern.compile("^@@\\s+-\\d+,?\\d*\\s+\\+(\\d+),?\\d*\\s+@@");

	for (String line : lines) {
		position++;  // 1-based position

		// Hunk header 찾기
		Matcher matcher = hunkPattern.matcher(line);
		if (matcher.find()) {
			currentLine = Integer.parseInt(matcher.group(1));  // 시작 라인 번호
			continue;
		}

		// 라인 타입별 처리
		if (line.startsWith("+")) {
			// 추가된 라인 → 매핑에 추가
			lineToPosition.put(currentLine, position);
			currentLine++;
		} else if (line.startsWith("-")) {
			// 삭제된 라인 → 파일에 없으므로 매핑 안 함
			// position만 증가
		} else if (!line.isEmpty()) {
			// Context 라인 (변경 안 됨) → 매핑에 추가
			lineToPosition.put(currentLine, position);
			currentLine++;
		}
	}

	return lineToPosition;
	// 결과: { 3:2, 4:3, 5:4, 6:5, 8:7, 9:8 }
}
```

#### 6.3. Review 생성 및 제출

```java
public void createReview(
	String repoFullName,
	int prNumber,
	String generalComment,
	List<InlineComment> inlineComments
) throws IOException {
	GHRepository repo = github.getRepository(repoFullName);
	GHPullRequest pr = repo.getPullRequest(prNumber);

	// 1. Diff 파싱하여 position 매핑 생성
	Map<String, Map<Integer, Integer>> lineToPositionMap = buildLineToPositionMap(pr);

	// 2. Review builder 생성
	GHPullRequestReviewBuilder reviewBuilder = pr.createReview();

	// 3. 전체 리뷰 코멘트 추가
	if (generalComment != null) {
		reviewBuilder.body(generalComment);
	}

	// 4. Inline comments 추가
	for (InlineComment comment : inlineComments) {
		Map<Integer, Integer> fileMap = lineToPositionMap.get(comment.getPath());

		if (fileMap != null && fileMap.containsKey(comment.getLine())) {
			int position = fileMap.get(comment.getLine());

			// ⭐ 핵심: position 사용!
			reviewBuilder.comment(
				comment.getBody(),     // "⚠️ validation 필요"
				comment.getPath(),     // "src/User.java"
				position               // 4 (diff의 position)
			);

			log.info("Added inline comment at {}:{} (position={})",
				comment.getPath(), comment.getLine(), position);
		} else {
			log.warn("Cannot find position for {}:{} - skipping",
				comment.getPath(), comment.getLine());
		}
	}

	// 5. Review 제출 (이게 없으면 draft 상태로 남음!)
	reviewBuilder.event(GHPullRequestReviewEvent.COMMENT);
	GHPullRequestReview review = reviewBuilder.create();

	log.info("Successfully created review #{}", review.getId());
}
```

---

## 핵심 개념

### 1. Feature 기반 리뷰

**일반적인 리뷰 시스템의 문제:**

```
PR에 100개 파일 변경
→ 모든 파일을 한꺼번에 리뷰
→ 맥락 없는 산발적인 코멘트
```

**Feature 기반 리뷰의 장점:**

```
PR에 100개 파일 변경
→ Feature별로 그룹화:
   - USER_AUTH: 10개 파일
   - PAYMENT: 5개 파일
   - NOTIFICATION: 3개 파일
→ 각 Feature의 맥락에서 리뷰
→ 일관성 있고 통찰력 있는 코멘트
```

**예시:**

Feature가 없는 경우:

```
❌ "이 메서드는 너무 길어요"
❌ "여기에 주석을 추가하세요"
```

Feature가 있는 경우:

```
✅ "USER_AUTHENTICATION Feature에서 세션 관리를 담당하는 이 메서드는
   과거 리뷰에서 지적된 토큰 만료 시간 이슈를 잘 해결했습니다.
   다만 refresh token 로직이 없어 UX가 저하될 수 있으니 추가를 권장합니다."
```

### 2. Feature Memory

**개념:** 각 Feature의 "진화 과정"을 기억

**저장 내용:**

- Feature의 목적과 설계 의도
- 과거 리뷰에서 지적된 사항
- 개선된 내용
- 주의해야 할 패턴

**예시:**

1차 PR (1월 10일):

```java
// 최초 구현
public void login(String username, String password) {
	User user = userRepository.findByUsername(username);
	if (user.getPassword().equals(password)) {
		createSession(user);
	}
}
```

LLM 리뷰:

```
⚠️ 평문 패스워드 비교는 보안상 매우 위험합니다.
BCrypt 등의 해시 알고리즘을 사용하세요.
```

Feature Memory 업데이트:

```
"USER_AUTHENTICATION Feature: 
2024-01-10 리뷰 - 패스워드를 평문으로 저장하고 있어 보안 위험.
BCrypt 해싱 도입 필요."
```

2차 PR (1월 20일):

```java
// 개선된 구현
public void login(String username, String password) {
	User user = userRepository.findByUsername(username);
	if (passwordEncoder.matches(password, user.getHashedPassword())) {
		createSession(user);
	}
}
```

LLM 리뷰 (Feature Memory 활용):

```
✅ 이전 리뷰에서 지적된 패스워드 평문 저장 이슈가 BCrypt로 개선되었습니다!
💡 추가로 로그인 시도 횟수 제한을 구현하면 brute force 공격도 방어할 수 있습니다.
```

Feature Memory 업데이트:

```
"USER_AUTHENTICATION Feature:
2024-01-10 리뷰 - 패스워드 평문 저장 위험.
2024-01-20 리뷰 - BCrypt 도입으로 개선됨. 
다음 개선 사항: 로그인 시도 횟수 제한."
```

### 3. Inline Comments vs General Review

**General Review:**

- PR 전체에 대한 총평
- "Conversation" 탭에 표시
- 전반적인 코드 품질, 구조, 패턴 논의

**Inline Comments:**

- 특정 코드 라인에 대한 구체적 지적
- "Files Changed" 탭의 해당 라인에 표시
- 즉시 수정 가능한 구체적 제안

**효과적인 사용:**

```
General Review:
"전반적으로 인증 로직이 잘 구현되었습니다. Spring Security의 
best practice를 잘 따르고 있으며, 이전 리뷰의 보안 이슈도 개선되었습니다."

Inline Comments:
Line 15: "⚠️ SQL injection 위험: PreparedStatement 사용 권장"
Line 23: "💡 이 로직은 AuthService로 분리하면 테스트가 용이합니다"
Line 45: "🐛 세션 타임아웃 체크가 누락되었습니다"
```

---

## 실제 동작 예시

### 시나리오: 로그인 기능 PR

**1. 개발자가 PR 생성**

```bash
git commit -m "feat: Add login validation"
git push origin feature/login-validation
# GitHub에서 PR 생성
```

**2. Webhook 발송**

```json
{
  "action": "opened",
  "pull_request": {
    "number": 123,
    "title": "feat: Add login validation",
    "user": {
      "login": "developer"
    }
  },
  "repository": {
    "full_name": "company/project"
  }
}
```

**3. 시스템 처리**

```
[WebhookController] PR #123 opened
  ↓
[FeatureRegistry] Loading .github/pr-review/feature-registry.yml
  ↓
[CodeCollector] Fetching changed files:
  - src/auth/LoginController.java (modified)
  - src/auth/ValidationService.java (added)
  ↓
[FeatureRegistry] Matching files to features:
  - LoginController.java → USER_AUTHENTICATION
  - ValidationService.java → USER_AUTHENTICATION
  ↓
[FeatureMemoryRepo] Loading memory for USER_AUTHENTICATION:
  "이전 리뷰에서 입력값 sanitization 누락 지적됨"
  ↓
[PromptBuilder] Building prompt:
  Feature: USER_AUTHENTICATION
  Memory: "이전 리뷰..."
  Code: LoginController.java (lines 1-50)
  ↓
[LlmClient] Calling Claude API...
  ↓
[Claude] Analyzing code...
  ✅ 입력값 validation 추가됨 (이전 지적 해결!)
  ⚠️ Line 23: Rate limiting 없음
  💡 Line 45: 에러 메시지에 민감한 정보 노출 위험
  ↓
[ReviewAggregator] Aggregating reviews
  ↓
[FeatureMemoryRepo] Updating memory:
  "2024-01-23: 입력값 validation 추가됨. 
   Rate limiting 및 에러 메시지 개선 필요"
  ↓
[GitHubReviewClient] Building review:
  1. Fetching PR diff
  2. Parsing patch to build position map
  3. Creating review with inline comments
  4. Submitting review
  ↓
[GitHub] Review posted! 🎉
```

**4. GitHub에 표시되는 결과**

Conversation 탭:

```
🤖 pr-review-bot commented:

# 전체 리뷰 결과

## USER_AUTHENTICATION 기능

전반적으로 입력값 validation이 잘 추가되었습니다. 
이전 리뷰에서 지적된 sanitization 이슈가 개선되었네요!

추가로 고려할 사항:
- Rate limiting 메커니즘
- 에러 메시지 보안 강화
```

Files Changed 탭:

```java
20:public LoginResponse login(LoginRequest request) {
	21:     // Validate input
	22:validateInput(request);
	23:return authService.authenticate(request);  
    💬 ⚠️Rate limiting이 없어 brute force 공격에 취약합니다.
		Spring Security의 rate limiter를 적용하세요.
...
	45:throw new Exception("Invalid password: " + password);
    💬 🚨에러 메시지에 패스워드가 노출되고 있습니다 !
		민감한 정보를 로그에 포함하지 마세요.
```

---

## 요약

### 핵심 플로우

```
GitHub PR → Webhook → Feature 분류 → Memory 조회 → LLM 분석 
→ Memory 업데이트 → Position 계산 → Review 게시
```

### 핵심 개념

1. **Feature 기반**: 코드를 기능 단위로 그룹화
2. **Feature Memory**: 진화 과정 기억
3. **Position 기반 Inline Comments**: Diff 파싱으로 정확한 위치에 코멘트

### 주요 도전과제

1. ✅ **Position 계산**: 라인 번호 → Diff position 변환
2. ✅ **Feature 매칭**: 파일 → Feature 매핑
3. ✅ **Memory 관리**: 일관성 있는 메모리 업데이트
4. ⏳ **Review 제출**: Event 타입 설정 필요 (테스트 대기 중)

이제 다른 AI가 작업을 이어받아도 전체 시스템을 이해하고 디버깅/개선할 수 있습니다! 🚀
