# Domain Index

PR Review Server의 전체 Feature를 도메인별로 분류한 인덱스입니다.

---

## api

API 입출력 및 Webhook 처리를 담당하는 도메인.

| Feature | 상태 | 설명 |
|---------|------|------|
| [webhook-receiver](api/features/webhook-receiver/SPEC.md) | 완료 | PR Webhook 수신 및 이벤트 처리 |
| [webhook-security](api/features/webhook-security/SPEC.md) | 미구현 | Webhook Secret HMAC 검증 |
| [github-app-auth](api/features/github-app-auth/SPEC.md) | 완료 | GitHub App JWT + Installation Token 인증 |
| [async-processing](api/features/async-processing/SPEC.md) | 미구현 | 비동기 리뷰 처리 (Webhook 즉시 응답) |

---

## review

코드 리뷰 핵심 파이프라인을 담당하는 도메인.

### Categories

#### pipeline

리뷰 실행의 핵심 파이프라인 컴포넌트.

| Feature | 상태 | 설명 |
|---------|------|------|
| [feature-registry](review/categories/pipeline/features/feature-registry/SPEC.md) | 완료 | YAML 기반 Feature 정의 및 Ant 패턴 매칭 |
| [feature-memory](review/categories/pipeline/features/feature-memory/SPEC.md) | 완료 | Feature별 학습 내용 저장/조회 |
| [code-collector](review/categories/pipeline/features/code-collector/SPEC.md) | 완료 | PR diff + Core files 수집 |
| [pr-parser](review/categories/pipeline/features/pr-parser/SPEC.md) | 완료 | PR 제목/본문에서 Feature 추출 |
| [prompt-builder](review/categories/pipeline/features/prompt-builder/SPEC.md) | 완료 | Feature 정보 + Memory + Code 기반 프롬프트 생성 |
| [llm-client](review/categories/pipeline/features/llm-client/SPEC.md) | 완료 | Claude API 호출 및 JSON 응답 파싱 |
| [review-aggregator](review/categories/pipeline/features/review-aggregator/SPEC.md) | 완료 | Feature별 리뷰 집계 + Memory 업데이트 |
| [github-review-client](review/categories/pipeline/features/github-review-client/SPEC.md) | 완료 | Position 기반 Inline Comments + General Review |

#### enhancement

리뷰 품질 개선 및 확장 기능.

| Feature | 상태 | 설명 |
|---------|------|------|
| [review-quality](review/categories/enhancement/features/review-quality/SPEC.md) | 미구현 | 프롬프트 엔지니어링 개선 + 리뷰 응답 품질 향상 |
| [two-stage-review](review/categories/enhancement/features/two-stage-review/SPEC.md) | 미구현 | LLM 추가 파일 요청 시 2차 리뷰 수행 |
| [review-comment-reply](review/categories/enhancement/features/review-comment-reply/SPEC.md) | 미구현 | 봇 리뷰 댓글에 대한 대화형 응답 |

#### config

리뷰 설정 및 LLM 확장.

| Feature | 상태 | 설명 |
|---------|------|------|
| [review-customization](review/categories/config/features/review-customization/SPEC.md) | 미구현 | Repository별 리뷰 설정 커스터마이징 |
| [multi-llm-support](review/categories/config/features/multi-llm-support/SPEC.md) | 미구현 | 다중 LLM 지원 (GPT, Gemini 등) |

---

## infrastructure

테스트 및 품질 관리를 담당하는 도메인.

| Feature | 상태 | 설명 |
|---------|------|------|
| [test-coverage](infrastructure/features/test-coverage/SPEC.md) | 미구현 | 핵심 컴포넌트 테스트 보강 (통합 테스트 포함) |

---

## saas

멀티 테넌트 SaaS 전환을 위한 도메인.

| Feature | 상태 | 설명 |
|---------|------|------|
| [user-auth](saas/features/user-auth/SPEC.md) | 미구현 | GitHub OAuth 로그인 + Spring Security |
| [tenant-isolation](saas/features/tenant-isolation/SPEC.md) | 미구현 | DB 마이그레이션 user_id 추가, 멀티 테넌트 격리 |
| [repository-management](saas/features/repository-management/SPEC.md) | 미구현 | GitHub App 설치 콜백 → 사용자-Repo 연결 |
| [usage-tracking](saas/features/usage-tracking/SPEC.md) | 미구현 | 사용자별 월간 리뷰 횟수 추적 (모니터링 전용) |

---

## web-ui

프론트엔드 UI를 담당하는 도메인.

| Feature | 상태 | 설명 |
|---------|------|------|
| [web-ui-auth](web-ui/features/web-ui-auth/SPEC.md) | 미구현 | Thymeleaf 로그인/회원가입/프로필 페이지 |
| [web-ui-dashboard](web-ui/features/web-ui-dashboard/SPEC.md) | 미구현 | 리뷰 히스토리, 통계, Repository 목록 대시보드 |
| [web-ui-settings](web-ui/features/web-ui-settings/SPEC.md) | 미구현 | Repository 설정, Feature Registry 편집기, 플랜 관리 |
| [review-dashboard](web-ui/features/review-dashboard/SPEC.md) | 미구현 | 리뷰 히스토리 조회 API + 간단한 대시보드 |