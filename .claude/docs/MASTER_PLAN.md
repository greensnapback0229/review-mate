# MASTER PLAN - PR Review Server

## 문제 요약

GitHub PR에 대해 AI 기반 자동 코드 리뷰를 제공하는 서버를 운영 중이다.
현재 MVP가 완성된 상태로, Feature 기반 리뷰 + Feature Memory + Inline Comments의 핵심 파이프라인이 동작한다.
다음 단계로 **리뷰 품질 향상**, **기능 확장**, 그리고 **멀티 테넌트 SaaS 전환**을 목표로 한다.

## 현재 구현 상태 (v1.0 - MVP)

| 컴포넌트 | 상태 | 설명 |
|-----------|------|------|
| Webhook 수신 | **완료** | `opened`, `synchronize` 이벤트 처리, 중복 방지 |
| Feature Registry | **완료** | YAML 기반 Feature 정의, Ant 패턴 매칭 |
| Feature Memory | **완료** | MySQL 기반 Feature별 학습 내용 저장/조회 |
| Code Collector | **완료** | PR diff + Core files 수집 |
| PR Parser | **완료** | PR 제목/본문에서 Feature 추출 |
| Prompt Builder | **완료** | Feature 정보 + Memory + Code 기반 프롬프트 생성 |
| LLM Client | **완료** | Claude Sonnet 4 API 호출, JSON 응답 파싱 |
| Review Aggregator | **완료** | Feature별 리뷰 집계 + Memory 업데이트 |
| GitHub Review Client | **완료** | Position 기반 Inline Comments + General Review |
| Draft PR 필터링 | **완료** | Draft PR은 리뷰 건너뜀 |
| GitHub App 인증 | **완료** | JWT + Installation Token, 토큰 만료 대응 |

### 미구현 항목 (코드에 TODO 존재)

- 2차 리뷰 (추가 파일 요청 → 재리뷰): `PrReviewService:175` - `break`로 건너뜀
- Webhook Secret 검증: 설정은 있으나 검증 로직 미구현
- 비동기 처리: 현재 동기적으로 리뷰 수행

## 가정 및 불명확한 요구사항

### 확정된 가정
1. 단일 GitHub App으로 여러 Repository를 지원한다
2. Feature Registry는 각 Repository의 `.github/pr-review/feature-registry.yml`에 정의한다
3. Feature Memory는 Repository별로 격리된다 (repositoryId 기반)
4. Claude Sonnet 4를 기본 리뷰 모델로 사용한다
5. 리뷰 언어는 한국어 기본 (프롬프트 한국어)
6. 사용자 인증은 GitHub OAuth로 처리한다
7. Repository 연결은 GitHub App 설치 콜백으로 자동화한다
8. 요금제: 무료 (월 30회 리뷰) / 유료 (무제한, 월 $15 API 비용 상한)
9. 결제는 Stripe 구독으로 처리한다
10. Web UI는 Spring + Thymeleaf로 구축한다 (동일 백엔드)

### 열린 질문
- [ ] 리뷰 언어를 Repository별로 설정 가능하게 할 것인가?
- [ ] Feature Memory의 최대 크기/보존 기간 정책은?
- [ ] 대용량 PR (50+ 파일) 처리 전략은? (분할 리뷰 vs 요약)
- [ ] Rate limiting 정책은? (GitHub API / Anthropic API)

## 제약 조건

| 제약 | 설명 |
|------|------|
| **GitHub API Rate Limit** | 5000 req/hour (Installation Token) |
| **Anthropic API** | Claude Sonnet 4, max 4000 tokens 응답 |
| **인프라** | Docker Compose 기반, MySQL 8.0, 단일 서버 |
| **인증 (리뷰)** | GitHub App (JWT + Installation Token) |
| **인증 (사용자)** | GitHub OAuth 2.0 + Spring Security |
| **결제** | Stripe Subscription (월 $15 상한) |
| **프론트엔드** | Spring + Thymeleaf (동일 서버) |
| **Java 21** | Spring Boot 3.3.6 |

---

## Feature 분해

### Phase 1: 리뷰 품질 향상 (우선)

| # | Feature | 설명 | 난이도 |
|---|---------|------|--------|
| F1 | **review-quality** | 프롬프트 엔지니어링 개선 + 리뷰 응답 품질 향상 | Medium |
| F2 | **two-stage-review** | LLM이 추가 파일 요청 시 2차 리뷰 수행 | Medium |
| F3 | **webhook-security** | Webhook Secret HMAC 검증 구현 | Easy |
| F4 | **test-coverage** | 핵심 컴포넌트 테스트 보강 (통합 테스트 포함) | Medium |

### Phase 2: 기능 확장

| # | Feature | 설명 | 난이도 |
|---|---------|------|--------|
| F5 | **async-processing** | 비동기 리뷰 처리 (Webhook 즉시 응답) | Medium |
| F6 | **review-customization** | Repository별 리뷰 설정 커스터마이징 | Medium |
| F7 | **multi-llm-support** | 다중 LLM 지원 (GPT, Gemini 등) | Hard |
| F8 | **review-dashboard** | 리뷰 히스토리 조회 API / 간단한 대시보드 | Hard |
| F9 | **review-comment-reply** | 봇 리뷰 댓글에 대한 대화형 응답 | Hard |

### Phase 3: 멀티 테넌트 SaaS 전환

| # | Feature | 설명 | 난이도 |
|---|---------|------|--------|
| F10 | **user-auth** | GitHub OAuth 로그인 + Spring Security + 사용자 DB | Hard |
| F11 | **tenant-isolation** | 기존 데이터 모델에 user_id 추가, 멀티 테넌트 격리 | Hard |
| F12 | **repository-management** | GitHub App 설치 콜백 → 사용자-Repo 연결, CRUD | Medium |
| F13 | **usage-tracking** | 사용자별 월간 리뷰 횟수 추적 + API 비용 추정 | Medium |
| F14 | **pricing-plans** | 무료(30회/월) / 유료(무제한, $15 상한) + Stripe 구독 | Hard |
| F15 | **web-ui-auth** | Thymeleaf 로그인/회원가입/프로필 페이지 | Medium |
| F16 | **web-ui-dashboard** | 리뷰 히스토리, 통계, Repository 목록 대시보드 | Medium |
| F17 | **web-ui-settings** | Repository 설정, Feature Registry 편집기, 플랜 관리 | Hard |

---

## 작업 순서 및 의존성

### 우선순위 그룹

| 우선순위 | 범위 | 목표 |
|----------|------|------|
| **P0** | F9 | 대화형 리뷰 댓글 응답 (가장 급한 기능) |
| **P1** | F10~F17 + F8 | 멀티 테넌트 SaaS 전환 (F8은 대시보드 데이터 필요) |
| **P2** | F1~F7 (F8 제외) | 리뷰 품질 향상 + 최적화 (후순위) |

```
P0: 대화형 리뷰 응답
═══════════════════

F9: review-comment-reply ─────────────────────────
    (의존성 없음, 현재 MVP 위에 바로 구현 가능)
    - issue_comment Webhook 핸들러
    - review_context 테이블 + 코드 스니펫/diff 저장
    - HEAD SHA 변경 감지 + 스레드 답글


P1: 멀티 테넌트 SaaS 전환
══════════════════════════

F10: user-auth ───────────────────────────────────┐
    (모든 SaaS 기능의 기반, GitHub OAuth)           │
                                                    │
F11: tenant-isolation ────────────────────────────┤  ← F10 직후
    (F10 완료 후, DB 마이그레이션 user_id 추가)     │
                                                    ▼
F12: repository-management ───────────────────┐
    (F10 + F11 완료 후, GitHub App 설치 연동)  │
                                               │  ← 병렬 가능
F15: web-ui-auth ─────────────────────────────┤
    (F10 완료 후, 로그인/프로필 페이지)         │
                                               ▼
F13: usage-tracking ──────────────────────────────
    (F12 완료 후, 사용자별 리뷰 카운팅)
                                                    │
F14: pricing-plans ─────────────────────────────────
    (F13 완료 후, Stripe 연동 + 사용량 제한)

F8: review-dashboard ─────────────────────────────
    (review_history 테이블 + 조회 API, F16에 데이터 제공)

F16: web-ui-dashboard ────────────────────────────
    (F12 + F8 완료 후, 대시보드 페이지)
                                                    │
F17: web-ui-settings ───────────────────────────────
    (F14 + F16 완료 후, 설정/플랜 관리 페이지)


P2: 리뷰 품질 향상 + 최적화 (후순위)
════════════════════════════════════

F3: webhook-security ──────────────────────────┐
    (독립 작업, HMAC 검증)                      │
                                                │  ← 병렬 가능
F1: review-quality ────────────────────────┐    │
    (프롬프트 엔지니어링 개선)              │    │
                                            │    │
F4: test-coverage ─────────────────────────┤    │
    (F1과 병렬 가능)                        │    │
                                            ▼    ▼
F2: two-stage-review ──────────────────────────────
    (F1 프롬프트 개선 후 진행 권장)

F5: async-processing ──────────────────────┐
    (비동기 리뷰 처리)                      │  ← 병렬 가능
F6: review-customization ──────────────────┤
                                            ▼
F7: multi-llm-support ─────────────────────────
    (F6 설정 시스템 필요)
```

### 권장 실행 순서

**P0: 대화형 리뷰 응답**
1. **F9: review-comment-reply** - 봇 리뷰 댓글에 대화형 응답, 현재 MVP 기반으로 즉시 착수

**P1: 멀티 테넌트 SaaS 전환**
2. **F10: user-auth** - GitHub OAuth + Spring Security (SaaS 기반)
3. **F11: tenant-isolation** - DB 스키마 마이그레이션 (user_id 추가)
4. **F12: repository-management** - GitHub App 설치 콜백 + Repo CRUD
5. **F15: web-ui-auth** - 로그인/회원가입 페이지 (F12와 병렬 가능)
6. **F13: usage-tracking** - 사용자별 리뷰 횟수/비용 추적
7. **F14: pricing-plans** - 무료/유료 플랜 + Stripe 구독
8. **F8: review-dashboard** - review_history 저장 + 조회 API (F16 데이터)
9. **F16: web-ui-dashboard** - 리뷰 대시보드 페이지
10. **F17: web-ui-settings** - 설정/Feature Registry 편집/플랜 관리

**P2: 리뷰 품질 향상 + 최적화 (후순위)**
11. **F3: webhook-security** - HMAC 검증 (독립, 빠르게 완료 가능)
12. **F1: review-quality** - 프롬프트 + 응답 파싱 개선
13. **F4: test-coverage** - 핵심 컴포넌트 테스트 보강
14. **F2: two-stage-review** - 기존 TODO 완성
15. **F5: async-processing** - 비동기 리뷰 처리
16. **F6: review-customization** - Repository별 리뷰 설정
17. **F7: multi-llm-support** - 다중 LLM 지원

---

## Feature별 SPEC 문서

각 Feature의 상세 명세는 아래 경로에서 관리:

### MVP 기능 (v1.0, 구현 완료)

| Feature | SPEC 경로 | 핵심 파일 |
|---------|-----------|-----------|
| webhook-receiver | `.claude/docs/features/webhook-receiver/SPEC.md` | WebhookController.java |
| feature-registry | `.claude/docs/features/feature-registry/SPEC.md` | FeatureRegistry.java, FeatureRegistryLoader.java |
| feature-memory | `.claude/docs/features/feature-memory/SPEC.md` | FeatureMemoryRepository.java, FeatureResolver.java |
| code-collector | `.claude/docs/features/code-collector/SPEC.md` | CodeCollector.java |
| pr-parser | `.claude/docs/features/pr-parser/SPEC.md` | PrParser.java |
| prompt-builder | `.claude/docs/features/prompt-builder/SPEC.md` | PromptBuilder.java |
| llm-client | `.claude/docs/features/llm-client/SPEC.md` | LlmClient.java |
| review-aggregator | `.claude/docs/features/review-aggregator/SPEC.md` | ReviewAggregator.java |
| github-review-client | `.claude/docs/features/github-review-client/SPEC.md` | GitHubReviewClient.java |
| github-app-auth | `.claude/docs/features/github-app-auth/SPEC.md` | GitHubAppAuthenticator.java, GitHubConfig.java |

### 신규 개선/확장 기능 (v2.0 로드맵)

| Feature | SPEC 경로 | 상태 |
|---------|-----------|------|
| F1: review-quality | `.claude/docs/features/review-quality/SPEC.md` | 미구현 |
| F2: two-stage-review | `.claude/docs/features/two-stage-review/SPEC.md` | 미구현 |
| F3: webhook-security | `.claude/docs/features/webhook-security/SPEC.md` | 미구현 |
| F4: test-coverage | `.claude/docs/features/test-coverage/SPEC.md` | 미구현 |
| F5: async-processing | `.claude/docs/features/async-processing/SPEC.md` | 미구현 |
| F6: review-customization | `.claude/docs/features/review-customization/SPEC.md` | 미구현 |
| F7: multi-llm-support | `.claude/docs/features/multi-llm-support/SPEC.md` | 미구현 |
| F8: review-dashboard | `.claude/docs/features/review-dashboard/SPEC.md` | 미구현 |
| F9: review-comment-reply | `.claude/docs/features/review-comment-reply/SPEC.md` | 미구현 |

### SaaS 전환 기능 (v3.0 로드맵)

| Feature | SPEC 경로 | 상태 |
|---------|-----------|------|
| F10: user-auth | `.claude/docs/features/user-auth/SPEC.md` | 미구현 |
| F11: tenant-isolation | `.claude/docs/features/tenant-isolation/SPEC.md` | 미구현 |
| F12: repository-management | `.claude/docs/features/repository-management/SPEC.md` | 미구현 |
| F13: usage-tracking | `.claude/docs/features/usage-tracking/SPEC.md` | 미구현 |
| F14: pricing-plans | `.claude/docs/features/pricing-plans/SPEC.md` | 미구현 |
| F15: web-ui-auth | `.claude/docs/features/web-ui-auth/SPEC.md` | 미구현 |
| F16: web-ui-dashboard | `.claude/docs/features/web-ui-dashboard/SPEC.md` | 미구현 |
| F17: web-ui-settings | `.claude/docs/features/web-ui-settings/SPEC.md` | 미구현 |

의사결정 로그: `.claude/docs/features/{feature}/DECISION_LOG.md`

---

## 범위 제외 항목 (Non-goals)

이번 로드맵에서 **의도적으로 제외**한 항목:

- **자동 코드 수정 제안 (Auto-fix)**: PR에 직접 코드 수정 커밋하는 기능
- **Slack/Discord 연동**: 리뷰 결과 메신저 알림
- **PR Approve/Request Changes**: 현재 COMMENT 이벤트만 사용 (자동 승인/변경요청 위험)
- **Multi-language Prompt**: 다국어 프롬프트 (현재 한국어 고정)
- **Self-hosted LLM**: 로컬 LLM 지원
- **GitHub Actions 통합**: GitHub Actions 워크플로우로 전환
- **팀/조직 관리**: 팀 단위 계정 (개인 사용자만 지원)
- **엔터프라이즈 SSO**: SAML/OIDC 기반 엔터프라이즈 인증
- **커스텀 도메인**: 사용자별 커스텀 도메인 지원

---

## 아키텍처 변경 예상

### Phase 1 (변경 최소)
- `WebhookController` - HMAC 검증 추가
- `PromptBuilder` - 프롬프트 구조 대폭 개선
- `LlmClient` - 응답 파싱 강화
- `PrReviewService` - 2차 리뷰 로직 완성
- 테스트 파일 대폭 추가

### Phase 2 (구조적 변경)
- 비동기 처리 도입 → `@Async` 또는 메시지 큐
- 설정 시스템 → Repository별 설정 테이블/YAML
- LLM 추상화 → Strategy 패턴으로 LLM Provider 분리
- 리뷰 결과 저장 → 새 테이블 추가 (review_history)
- 리뷰 댓글 응답 → review_context 테이블 + Comment Webhook Handler + 스레드 답글 API

### Phase 3 (아키텍처 대폭 변경)
- **인증 체계**: Spring Security + GitHub OAuth2 Client + JWT 세션
- **DB 마이그레이션**: 기존 테이블에 `user_id` FK 추가, `users` 테이블 신규
- **멀티 테넌트**: 모든 쿼리에 user_id 조건 추가, 데이터 격리
- **GitHub App 설치 플로우**: 설치 콜백 → 사용자-Repository 자동 연결
- **사용량 추적**: `usage_log` 테이블, 월간 리뷰 카운팅 + API 비용 추정
- **결제**: Stripe Checkout + Subscription API, Webhook으로 결제 상태 동기화
- **프론트엔드**: Thymeleaf 템플릿 + Bootstrap/Tailwind, 같은 Spring Boot 앱에서 서빙
- **새 DB 테이블**: `users`, `user_repositories`, `usage_log`, `subscriptions`