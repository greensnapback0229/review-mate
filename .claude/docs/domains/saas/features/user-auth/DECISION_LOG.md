# F10: User Auth - DECISION_LOG

## D1: 암호화 방식 선택

**날짜**: 2026-02-10
**상태**: 확정

### 결정
Spring Security Crypto의 `Encryptors.text()` (AES-256-CBC) 사용

### 대안
| 방안 | 장점 | 단점 |
|------|------|------|
| **Spring Encryptors.text()** (채택) | Spring 내장, 추가 의존성 없음, 검증된 구현 | CBC 모드 (GCM 대비 약간 낮은 보안) |
| AES-256-GCM 직접 구현 | 최신 AEAD 방식, 무결성 보장 | 직접 구현 복잡, 실수 위험 |
| Jasypt | 설정 파일 암호화에 특화 | 추가 의존성, 과도한 기능 |

### 근거
- Spring Boot 프로젝트에서 추가 의존성 없이 바로 사용 가능
- `TextEncryptor` 인터페이스로 추상화되어 향후 교체 용이
- Salt는 secretKey 기반 SHA-256 해시의 앞 8바이트 사용 (고정 salt, 서버 재시작 시에도 복호화 가능)

---

## D2: LlmClient API Key 전달 방식

**날짜**: 2026-02-10
**상태**: 확정

### 결정
메서드 파라미터로 `String apiKey`를 첫 번째 인자로 전달, 호출마다 `AnthropicOkHttpClient` 인스턴스 생성

### 대안
| 방안 | 장점 | 단점 |
|------|------|------|
| **메서드 파라미터 방식** (채택) | 간단, 명시적, 스레드 안전 | 매 호출마다 클라이언트 생성 오버헤드 |
| API Key별 클라이언트 캐시 (ConcurrentHashMap) | 성능 최적화, 재사용 | 캐시 관리 복잡, 메모리 누수 위험, Key 변경 시 캐시 무효화 필요 |
| ThreadLocal / RequestScope | Spring 스코프 활용 | 복잡, 비동기 전환 시 문제 |

### 근거
- 현재 동기 처리 + 단일 서버 환경에서 클라이언트 생성 오버헤드는 무시 가능
- API Key가 변경되어도 즉시 반영됨 (캐시 무효화 불필요)
- 향후 성능 이슈 발생 시 캐시 방식으로 전환 가능 (인터페이스 변경 없음)

---

## D3: Webhook에서 API Key 미설정 시 처리 전략

**날짜**: 2026-02-10
**상태**: 확정

### 결정
- PR 리뷰 요청 시: 리뷰 스킵 + PR에 안내 코멘트 게시
- 댓글 응답 요청 시: 조용히 스킵 (코멘트 없음)

### 대안
| 방안 | 장점 | 단점 |
|------|------|------|
| **PR 코멘트 안내** (채택) | 사용자에게 명확한 피드백, 설정 유도 | PR에 봇 코멘트 추가 |
| HTTP 에러 응답만 | 구현 간단 | 사용자가 왜 리뷰가 안 되는지 모름 |
| GitHub Check 생성 | GitHub UI에 상태 표시 | 추가 API 호출, 복잡도 증가 |

### 근거
- Repository 소유자의 API Key 조회 경로: `repositoryId` → `Repository.userId` → `User.anthropicApiKey` → 복호화
- PR 리뷰는 사용자가 기대하는 동작이므로 명시적 안내 필요
- 댓글 응답은 부가 기능이므로 조용히 스킵해도 무방

---

## D4: 서비스 레벨 API Key 제거

**날짜**: 2026-02-10
**상태**: 확정

### 결정
`application.yml`에서 `anthropic.api.key` 설정 완전 제거. 모든 LLM 호출은 사용자별 Key 사용.

### 근거
- SaaS 전환의 핵심 요구사항: 각 사용자가 자신의 Anthropic API Key를 사용
- 서비스 운영자가 API 비용을 부담하지 않는 무료 서비스 모델
- `LlmClient` 생성자에서 `@Value` 어노테이션 제거, `ObjectMapper`만 주입
- API Key null/empty 시 `IllegalArgumentException` 발생으로 안전장치 확보

---

## D5: 첫 가입자 ADMIN 처리 및 데이터 마이그레이션

**날짜**: 2026-02-10
**상태**: 확정

### 결정
`users` 테이블이 비어있을 때 첫 번째 가입자를 `ADMIN`으로 지정하고, `user_id IS NULL`인 기존 데이터를 해당 사용자에게 귀속

### 마이그레이션 대상
- `repositories` 테이블
- `feature_memory` 테이블
- `review_context` 테이블

### Race Condition 대응
- `github_id` UNIQUE 제약으로 동시 가입 시 1명만 성공
- 나머지는 일반 `USER`로 처리

### 근거
- MVP 단계에서 쌓인 데이터를 최초 관리자에게 자연스럽게 귀속
- 별도 마이그레이션 스크립트 없이 OAuth 로그인 플로우에서 자동 처리
