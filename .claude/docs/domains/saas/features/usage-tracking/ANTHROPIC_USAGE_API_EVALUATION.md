# Anthropic Usage Cost API 도입 평가 보고서

**평가 일시**: 2026-02-19
**요청 배경**: Anthropic Usage & Cost Admin API를 활용하여 사용자 토큰 사용량을 추적하는 방안 검토
**참조 문서**: https://platform.claude.com/docs/en/build-with-claude/usage-cost-api
**결론**: ❌ **도입 불필요** — 기존 F13 inline 방식이 우리 아키텍처에 더 적합

---

## 1. Anthropic Usage Cost API 개요

Anthropic이 제공하는 조직(워크스페이스) 수준의 사용량·비용 조회 Admin API.

### 제공 데이터

**Usage Endpoint** (`GET /v1/organizations/usage_report/messages`):
- 토큰 수: uncached input / cached input / cache creation / output
- 필터: 모델, API Key ID, 워크스페이스, 서비스 티어
- 시간 버킷: `1m`, `1h`, `1d`

**Cost Endpoint** (`GET /v1/organizations/cost_report`):
- 실제 청구 금액 (USD, 센트 단위 decimal string)
- 일별(`1d`) 집계만 지원

### 인증 요건

- **Admin API Key** (`sk-ant-admin...`) 별도 필요
- 일반 API Key(`sk-ant-api...`)로는 호출 불가
- **조직(Organization) 계정 필수** — 개인 계정 사용 불가
- 조직 내 Admin 역할을 가진 멤버만 Admin Key 발급 가능

---

## 2. 현재 F13 구현 방식

| 항목 | 내용 |
|------|------|
| **추적 시점** | LLM 응답 직후 (`Message.usage()`) |
| **저장 단위** | 기능(feature)별 리뷰 1건 |
| **비용 계산** | Anthropic 공식 단가 기반 추정 (`$3/1M input`, `$15/1M output`) |
| **필요 인증** | 사용자 일반 API Key (이미 보유) |
| **지연** | 없음 (실시간) |
| **격리** | `user_id` FK로 테넌트 격리 완료 |

---

## 3. 비교 분석

| 항목 | F13 현재 방식 (inline) | Anthropic Usage Cost API |
|------|----------------------|--------------------------|
| **지연시간** | ✅ 실시간 | ⚠️ 최대 5분 지연 |
| **필요 인증** | ✅ 일반 API Key (이미 보유) | ❌ Admin API Key 별도 필요 |
| **조직 계정** | ✅ 불필요 | ❌ 필수 (개인 계정 불가) |
| **비용 데이터** | ⚠️ 추정값 (단가 기반) | ✅ 실제 청구 금액 |
| **세분화 수준** | ✅ 기능(feature)별 | ⚠️ API Key별 집계 |
| **구현 복잡도** | ✅ 이미 완료 | ❌ 추가 구현 필요 |
| **UX 마찰** | ✅ 없음 | ❌ 사용자에게 Admin Key 추가 요청 |
| **캐시 토큰 추적** | ⚠️ Sonnet SDK 제공 여부에 따름 | ✅ 캐시 생성/읽기 세분화 |

---

## 4. 도입 불필요 근거

### 4.1 인증 마찰 (가장 중요)

우리 SaaS 모델은 **사용자가 자신의 Anthropic API Key를 제공**하는 방식이다.
Usage Cost API 도입 시 사용자는:

1. Anthropic Console에서 **조직(Organization)** 설정 필요
2. **Admin API Key** 별도 발급
3. 우리 서비스에 일반 API Key + Admin API Key **두 가지** 등록

이는 현재 단순한 API Key 하나 등록에서 대폭 복잡해지며, 개인 계정 사용자는 아예 사용 불가.

### 4.2 기능 세분화 손실

Usage Cost API는 **API Key 수준** 집계이다.
우리 F13은 **기능(feature)별** 세분화로, 어떤 기능 리뷰에서 얼마나 소모했는지 추적 가능.
API 집계로는 이 세분화가 불가능하다.

### 4.3 비용 추정 정확도 충분

Anthropic이 공식 문서에 게시한 단가를 사용하므로 추정치와 실제 청구액 간 차이는 미미하다.
(Cache hit discount 등 일부 차이 가능하지만 SaaS 무료 운영 모델에서 허용 범위)

---

## 5. 잠재적 도입 가치

❌ 현시점에서는 도입 불필요하지만, 향후 **서비스 레벨 API Key** 도입 시 검토 가치 있음:

- 서비스가 단일 Anthropic 계정으로 API 비용을 부담하는 모델로 전환 시
- API Key별 실제 비용 검증/정산이 필요해지는 엔터프라이즈 기능 추가 시
- 캐시 토큰 최적화 추적이 중요해지는 시점

---

## 6. 결정

> **기존 F13 inline 방식 유지**. Anthropic Usage Cost API 도입 없음.

F13이 제공하는 기능별 실시간 토큰 추적은 현재 SaaS 모델(사용자 소유 API Key)에 최적화되어 있으며, 추가 인증 부담 없이 정확한 사용량 데이터를 제공한다.

---

*이 문서는 아키텍처 결정 기록(ADR)으로 보존됩니다.*