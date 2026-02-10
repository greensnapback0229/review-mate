# F16: Web UI Dashboard - SPEC

## 개요
사용자의 리뷰 현황을 한눈에 볼 수 있는 대시보드 페이지.
Repository 목록, 최근 리뷰, 사용량 통계를 표시한다.

## 상태: 미구현

## 관련 파일 (예정)
- `DashboardController.java` - 대시보드 컨트롤러
- `templates/dashboard.html` - 대시보드 페이지
- `templates/reviews.html` - 리뷰 히스토리 페이지
- `templates/fragments/stats-card.html` - 통계 카드 컴포넌트

## 범위 정의

### In-Scope
- 대시보드 메인 페이지 (요약 통계)
- 연결된 Repository 목록 + 상태
- 최근 리뷰 히스토리 (페이징)
- 월간 사용량 표시 (리뷰 수, 비용, 남은 횟수)
- Repository별 리뷰 상세 보기
- PR별 리뷰 결과 상세 보기

### Out-of-Scope
- 실시간 리뷰 진행 상황 (WebSocket)
- 차트/그래프 (차후 추가)
- 리뷰 결과 수정/삭제

## 의존성
- **의존**: F8 (review-dashboard) → review_history 데이터
- **의존**: F12 (repository-management) → Repository 목록
- **의존**: F13 (usage-tracking) → 사용량 데이터
- **의존**: F15 (web-ui-auth) → 공통 레이아웃

## 상세 설계

### 페이지 목록

| URL | 템플릿 | 설명 |
|-----|--------|------|
| `/dashboard` | `dashboard.html` | 메인 대시보드 (요약) |
| `/reviews` | `reviews.html` | 전체 리뷰 히스토리 (페이징) |
| `/reviews/{repoId}` | `repo-reviews.html` | Repository별 리뷰 |
| `/reviews/{repoId}/pr/{prNumber}` | `pr-review-detail.html` | PR별 상세 리뷰 |

### 대시보드 레이아웃
```
┌──────────────────────────────────────────────────┐
│  [네비게이션 바]                                    │
├──────────────────────────────────────────────────┤
│                                                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ 총 리뷰   │ │ 이번 달   │ │ 남은 횟수 │           │
│  │   150    │ │   18/30  │ │    12    │           │
│  └──────────┘ └──────────┘ └──────────┘           │
│                                                    │
│  ┌─ 연결된 Repository ──────────────────────────┐  │
│  │ owner/repo1  ● Active   42 reviews  [설정]   │  │
│  │ owner/repo2  ● Active   15 reviews  [설정]   │  │
│  │ owner/repo3  ○ Inactive  0 reviews  [연결]   │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  ┌─ 최근 리뷰 ─────────────────────────────────┐  │
│  │ PR #42 "feat: Add auth"  AUTH  ✅ 5 comments │  │
│  │ PR #41 "fix: payment"   PAY   ✅ 3 comments │  │
│  │ PR #40 "refactor: user" USER  ❌ Failed      │  │
│  │                          [더 보기 →]         │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  [푸터]                                            │
└──────────────────────────────────────────────────┘
```

### Thymeleaf 모델 데이터

```java
// DashboardController
model.addAttribute("stats", usageService.getCurrentMonthStats(userId));
model.addAttribute("repositories", repoService.getUserRepositories(userId));
model.addAttribute("recentReviews", reviewService.getRecentReviews(userId, 10));
```

## 에러 처리 정책

| 상황 | 동작 | 영향 |
|------|------|------|
| review_history 조회 실패 | "리뷰 데이터를 불러올 수 없습니다" 표시 | 빈 리뷰 목록 |
| Repository 목록 조회 실패 | 에러 메시지 표시 | 빈 목록 |
| 사용량 데이터 없음 | 0으로 표시 | 정상 |
| 타인의 Repository 접근 | 403 에러 페이지 | 접근 차단 |

## 테스트 케이스
1. /dashboard → 대시보드 렌더링 (통계, Repo 목록, 최근 리뷰)
2. /reviews → 페이징된 리뷰 히스토리
3. /reviews/{repoId}/pr/{prNumber} → PR 상세 리뷰 (인라인 코멘트 포함)
4. 타인의 Repository → 403
5. 데이터 없는 신규 사용자 → 빈 대시보드 + 가이드 메시지

## 완료 조건
- [ ] 대시보드 메인 페이지 (통계 카드 3개)
- [ ] Repository 목록 표시
- [ ] 최근 리뷰 히스토리 (페이징)
- [ ] PR별 리뷰 상세 페이지
- [ ] 반응형 디자인
- [ ] 단위 테스트 5개 이상
