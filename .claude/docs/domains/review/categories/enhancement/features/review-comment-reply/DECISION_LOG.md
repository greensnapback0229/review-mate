# DECISION LOG - F9: Review Comment Reply

---

## DL-01: 봇 답글 감지 방식

**날짜**: 2026-02-20
**상태**: 확정

### 컨텍스트

`pull_request_review_comment` 이벤트가 수신되면, 해당 댓글이 "봇이 작성한 리뷰 코멘트에 대한 답글"인지 판별해야 한다. 잘못 감지하면 무한 루프(봇이 봇 자신의 답글에 응답) 또는 무관한 대화에 끼어드는 문제가 발생한다.

### 고려한 옵션

**A. GitHub API 호출로 원본 코멘트 작성자 확인**
`in_reply_to_id`로 원본 코멘트를 조회하고, 작성자 login이 봇 계정인지 확인한다.
- 장점: 직관적, 봇 계정명만 알면 동작
- 단점: 매 답글마다 추가 GitHub API 호출 필요 (Rate Limit 소비), 봇 계정명이 환경변수로 관리되어야 함

**B. 봇 코멘트 ID를 DB에 저장하고 조회 (채택)**
1차 리뷰 시 GitHub에 게시한 코멘트 ID를 `review_context.bot_comment_ids` JSON 배열에 저장한다. 답글 수신 시 `in_reply_to_id`가 이 배열에 포함되는지 DB 조회로 확인한다.
- 장점: GitHub API 추가 호출 없음, 다중 턴(봇 답글의 답글)도 ID 누적으로 자연스럽게 지원
- 단점: 1차 리뷰 시 코멘트 ID 저장 로직이 필수, DB 조회 필요

**C. 봇 계정명을 Webhook payload의 comment.user.login과 직접 비교**
`in_reply_to_id`로 원본 댓글을 가져오지 않고, payload에서 바로 비교. 단 payload에는 원본 작성자 정보가 없어 별도 API 호출이 결국 필요하다.

### 결정: **B - DB 기반 bot_comment_ids**

API 호출을 추가하지 않고 다중 턴 대화를 구조적으로 지원할 수 있다. 봇이 새 답글을 달 때마다 코멘트 ID를 배열에 append하면, 개발자가 봇의 2차·3차 답글에 다시 답해도 감지 가능하다. MySQL `JSON_CONTAINS` 함수로 O(n) 조회가 가능하며, 코멘트 수가 PR당 최대 100개로 제한되어 성능 문제가 없다.

### 재검토 조건

- GitHub App이 여러 봇 계정을 사용하게 되면 A 방식(계정명 비교)도 고려할 것.
- bot_comment_ids가 100개를 초과하는 PR이 빈번하게 발생하면 별도 테이블 분리 검토.

---

## DL-02: 리뷰 컨텍스트 저장 단위

**날짜**: 2026-02-20
**상태**: 확정

### 컨텍스트

1차 리뷰 결과(파일 요약, 코드 스니펫, diff, 인라인 코멘트)를 DB에 저장할 때, 한 PR의 여러 Feature를 어떤 단위로 저장할지 결정해야 한다. 저장 단위는 이후 댓글 응답 시 "어떤 컨텍스트를 로드하는가"에 직접 영향을 미친다.

### 고려한 옵션

**A. PR별 1행**
모든 Feature의 컨텍스트를 하나의 행에 JSON으로 합산 저장.
- 장점: 단순한 조회 (WHERE repo_id AND pr_number)
- 단점: 댓글이 달린 파일과 무관한 다른 Feature 컨텍스트까지 LLM에 전달 → 불필요한 토큰 낭비, 관련 없는 정보로 응답 품질 저하

**B. Feature별 1행 (채택)**
PR + Feature 조합을 유니크 키로 삼아 Feature별로 독립 저장 (`UNIQUE INDEX on repo_id, pr_number, feature_name`).
- 장점: 댓글이 달린 파일 경로 → 해당 Feature 컨텍스트만 로드 (토큰 절약), Feature 단위로 컨텍스트 갱신 가능
- 단점: 조회 시 파일 경로 → Feature 매핑 로직 필요

**C. 파일별 1행**
변경된 파일마다 행을 생성.
- 장점: 최소 단위로 정밀한 컨텍스트 로드
- 단점: 파일 수가 많으면 행 수 폭증, Feature 전체 맥락을 잃음

### 결정: **B - Feature별 1행**

PR의 댓글은 특정 파일의 특정 라인에 달리며, 해당 파일은 보통 하나의 Feature에 속한다. Feature 단위로 컨텍스트를 분리하면 관련 코드만 LLM에 전달하여 토큰을 절약하고, Feature 경계를 명확하게 유지할 수 있다. 기존 `FeatureRegistry`의 파일→Feature 매핑 로직을 재사용하면 추가 복잡도도 최소화된다.

---

## DL-03: file_contexts 저장 내용 (요약만 vs 코드 포함)

**날짜**: 2026-02-20
**상태**: 확정

### 컨텍스트

리뷰 컨텍스트에 파일 정보를 어느 수준까지 저장할지 결정한다. 저장 내용이 많을수록 댓글 응답 시 GitHub API 재조회를 줄일 수 있지만, DB 저장 비용과 JSON 크기가 커진다.

### 고려한 옵션

**A. 파일 요약(summary)만 저장**
LLM이 생성한 한 문단 요약만 보관.
- 장점: 저장 용량 최소
- 단점: 개발자가 "45번째 줄 왜 이렇게 했어요?" 물을 때 실제 코드 없이 요약만으로 답변 → 구체성 부족

**B. 요약 + 코드 스니펫 + diff 저장 (채택)**
`summary`, `keyLines`, `changes`, `codeSnippets[]`, `diff`를 JSON으로 함께 저장.
- 장점: 댓글 응답 시 해당 파일을 GitHub API로 재조회하지 않아도 됨, 라인 번호와 실제 코드를 참조한 구체적 답변 가능
- 단점: 파일당 JSON 크기 증가 (평균 ~1-3 KB)

### 결정: **B - 코드 스니펫 + diff 포함 저장**

코드 리뷰 답변의 핵심은 "어떤 코드 때문에 그 코멘트를 달았는가"를 다시 참조하는 것이다. 요약만으로는 라인 단위 질문에 대응하기 어렵고, 댓글마다 GitHub API를 호출하면 Rate Limit 소비도 커진다. 리뷰 시점의 스니펫과 diff를 보존함으로써 "리뷰 당시와 현재 코드가 다른지"도 HEAD SHA 비교와 함께 정밀하게 감지할 수 있다.

### 재검토 조건

- 평균 file_contexts JSON이 10 KB를 초과하는 대형 PR이 빈번해지면 스니펫 길이 상한(예: 파일당 100줄) 도입 검토.

---

## DL-04: 코드 변경 감지 전략

**날짜**: 2026-02-20
**상태**: 확정

### 컨텍스트

1차 리뷰 이후 개발자가 코드를 수정하고 답글을 달 수 있다. 이 경우 저장된 컨텍스트가 이미 구버전 코드를 참조하고 있어, LLM이 "이미 수정된 문제"를 다시 지적하거나 잘못된 답변을 생성할 수 있다.

### 고려한 옵션

**A. 항상 GitHub API로 최신 코드 조회**
댓글 수신 시 매번 파일 최신 버전을 가져옴.
- 장점: 항상 최신 상태
- 단점: 불필요한 API 호출, 코드가 바뀌지 않았을 때도 네트워크 비용 발생

**B. HEAD SHA 비교 후 변경된 경우에만 재조회 (채택)**
저장된 `head_sha`와 PR의 현재 HEAD SHA를 비교해, 다를 때만 해당 파일의 최신 코드를 GitHub API로 조회하고 LLM 프롬프트에 "변경 감지" 섹션을 추가.
- 장점: API 호출 최소화, 변경 사실을 LLM에 명시적으로 전달하여 답변 품질 향상
- 단점: PR HEAD SHA 조회 API 1회 추가 (경량)

**C. 변경 감지 없이 저장된 컨텍스트만 사용**
구현 단순화.
- 단점: 구버전 코드 참조로 인한 오답 가능성, 이미 수정된 문제를 다시 지적하는 상황 발생

### 결정: **B - HEAD SHA 비교 + 조건부 재조회**

PR HEAD SHA 조회는 경량 API(PR 메타데이터 1회)이며, 코드가 변경된 경우 LLM 프롬프트에 "리뷰 시점 코드"와 "현재 코드"를 나란히 제공하여 "이미 고쳤는데요"라는 개발자의 반박에 정확하게 응답할 수 있다. 변경 없음이 대부분의 케이스이므로 API 비용도 낮다.

---

## DL-05: 스레드 답글 횟수 상한

**날짜**: 2026-02-20
**상태**: 확정

### 컨텍스트

봇이 동일 스레드에서 무제한으로 응답하면 GitHub 알림 스팸, Anthropic API 비용 폭증, 무한 루프 리스크가 발생한다. 적절한 상한을 설정해야 한다.

### 결정: **스레드당 최대 10회 봇 응답**

일반적인 코드 리뷰 토론은 3-5회 왕복으로 해소된다. 10회는 충분한 여유를 두면서도 비정상적인 루프를 차단한다. `bot_comment_ids` 배열 크기로 간단하게 카운팅할 수 있다. 10회 초과 시 봇은 응답을 생략하고 에러 로그만 남긴다 (사용자 혼란 방지를 위해 "더 이상 응답할 수 없습니다" 메시지 게시는 선택적으로 추가 가능).

### 재검토 조건

- 실제 운영 데이터에서 5회 초과 대화가 빈번하게 발생하면 상한 조정 또는 스레드 요약 후 재응답 방식 도입 검토.

---

## DL-06: review_context unique constraint에 user_id 포함 여부

**날짜**: 2026-02-20
**상태**: 확정

### 컨텍스트

SaaS 배포 후 PR synchronize 이벤트(새 커밋 push)가 발생했을 때 `review_context` 저장 시 `Duplicate entry` 오류가 발생했다. 원인은 unique constraint가 `(repository_id, pr_number, feature_name)` 3컬럼으로 구성되어 `user_id`가 빠져 있는 반면, upsert 조회 쿼리는 `findByRepositoryIdAndPrNumberAndFeatureNameAndUserId`로 4컬럼을 필터링하기 때문이다.

시나리오:
1. 이전 배포(SaaS 이전) 또는 `user_id`가 NULL인 row가 DB에 존재
2. 신규 배포에서 synchronize 이벤트 수신
3. `find(..., userId=1)` → NULL row를 찾지 못함 → INSERT 시도
4. 이미 `(repo, pr, feature)` 조합이 존재 → **Duplicate entry**

또한 SaaS 설계상 동일 레포지토리에 N:N으로 연결된 여러 사용자가 같은 PR을 각자 독립적으로 리뷰할 수 있어야 한다. 이 경우 user_id가 없는 unique constraint는 두 번째 사용자의 INSERT를 막아 다중 사용자 리뷰 자체가 불가능해진다.

### 고려한 옵션

**A. unique constraint를 `(repository_id, pr_number, feature_name, user_id)`로 변경 (채택)**
user_id를 포함시켜 사용자별로 독립된 review context row를 허용한다.
- 장점: 다중 사용자 리뷰 지원, upsert 쿼리와 constraint가 일치하여 논리적 일관성 확보
- 단점: 기존 NULL user_id row와 신규 user_id row가 공존 → 구 데이터는 조회되지 않아 orphan으로 남음 (허용 가능)

**B. constraint는 유지하고 upsert 쿼리를 user_id 없이 조회**
find 시 user_id를 필터에서 제외하여 기존 row를 찾아 UPDATE.
- 단점: 다중 사용자 환경에서 서로 다른 사용자의 review context를 덮어쓰는 심각한 데이터 오염 발생. SaaS 설계 원칙(테넌트 격리)에 위배.

**C. NULL user_id row를 신규 userId로 마이그레이션 후 find**
upsert 시 user_id 기반 find가 빈 값이면 NULL user_id row도 조회해 adopt.
- 단점: 구현 복잡도 증가, 어떤 user_id를 adopt해야 하는지 불명확 (다중 사용자 환경에서 모호성 발생).

### 결정: **A - user_id를 unique constraint에 포함**

테넌트 격리 원칙상 review context는 사용자별로 완전히 독립되어야 한다. upsert 쿼리가 이미 `user_id`를 기준으로 동작하므로 constraint와 쿼리를 일치시키는 것이 가장 논리적이며, 코드 변경 없이 constraint 수정만으로 해결된다. 기존 NULL user_id row는 신규 쿼리에 조회되지 않아 orphan으로 남지만 기능에 영향을 주지 않는다.

`ddl-auto: update` 설정으로 배포 시 기존 constraint가 자동으로 DROP되고 새 constraint가 적용된다.

### 재검토 조건

- NULL user_id orphan row가 대량으로 쌓여 스토리지 문제가 발생하면 정리 스크립트 작성 검토.
- 향후 동일 PR을 여러 사용자가 리뷰하는 N:N 시나리오가 실제 운영에서 빈번해지면 사용자별 review context 병합 또는 대표 컨텍스트 선택 정책 도입 검토.