# F16: web-ui-dashboard SPEC

## 상태: **완료**

## 목표
대시보드 웹 UI — 연결된 저장소 목록, 최근 리뷰 이력, 이번 달 사용량 통계를 한 화면에 제공.
저장소별 상세 페이지에서 리뷰 이력 및 기능별 분포를 페이지네이션과 함께 조회.

## 의존성
- F8 (review-dashboard): ReviewHistoryService, RepositoryStatsResponse
- F12 (repository-management): UserRepositoryService
- F13 (usage-tracking): UsageService
- F15 (web-ui-auth): layout/base.html, 인증 흐름

## 라우트

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | `/dashboard` | 메인 대시보드 | 필요 |
| GET | `/repositories/{repositoryId}` | 저장소 상세 + 리뷰 이력 | 필요 |

## 대시보드 (`/dashboard`)

### Model 데이터
| 속성 | 타입 | 출처 |
|------|------|------|
| `user` | `User` | `principal.getUser()` |
| `repositories` | `List<UserRepository>` | `UserRepositoryService.findActiveRepositoriesByUserId()` |
| `recentReviews` | `List<ReviewSummaryDto>` | `ReviewHistoryService.getReviewHistory()` (최근 10건) |
| `usage` | `UsageSummary` | `UsageService.getCurrentMonthUsage()` |

### UI 구성
- **통계 카드 4개**: 연결된 저장소 수 / 이번 달 리뷰 수 / 총 토큰(K) / 추정 비용($)
- **연결된 저장소 목록**: 각 항목 클릭 시 `/repositories/{id}` 이동
- **최근 리뷰 테이블**: PR 번호, 제목(30자 truncate), 기능명, 상태(완료/실패), 일시

## 저장소 상세 (`/repositories/{repositoryId}`)

### 접근 제어
- 인증된 사용자의 `userId`로 `findActiveRepositoriesByUserId()` 조회 후 `repositoryId` 필터
- 해당 사용자에 연결되지 않은 저장소 접근 시 → **HTTP 404** (`ResponseStatusException`)

### Model 데이터
| 속성 | 타입 | 출처 |
|------|------|------|
| `repository` | `UserRepository` | 사용자 저장소 목록에서 필터 |
| `reviews` | `Page<ReviewSummaryDto>` | `ReviewHistoryService.getReviewsByRepository()` (20건/페이지) |
| `stats` | `RepositoryStatsResponse` | `ReviewHistoryService.getRepositoryStats()` |

### UI 구성
- **통계 카드 4개**: 전체 리뷰 수 / 완료 / 실패 / 평균 인라인 댓글
- **기능별 분포 카드**: `reviewsByFeature` Map 렌더링 + 최근 7일/30일 건수
- **리뷰 이력 테이블**: PR 번호, 제목(35자), 기능명, 인라인 댓글 수, 상태, 일시
- **페이지네이션**: `totalPages > 1`일 때만 표시

## SecurityConfig
`/repositories/**` → `.authenticated()` 규칙 추가

## 구현 파일
- `web/WebPageController.java` — dashboard/repositoryDetail 메서드 추가
- `config/SecurityConfig.java` — `/repositories/**` authenticated 추가
- `templates/dashboard.html` — 통계카드 + 저장소목록 + 최근리뷰
- `templates/repositories/detail.html` — 통계카드 + 기능분포 + 리뷰테이블 + 페이지네이션

## 테스트 (WebPageControllerTest)

| 케이스 | 결과 |
|--------|------|
| 인증 사용자 /dashboard → model(repositories/recentReviews/usage) | ✅ |
| 미인증 사용자 /dashboard → 302 /login | ✅ |
| 인증 사용자 본인 저장소 /repositories/{id} → model(repository/reviews/stats) | ✅ |
| 타인 저장소 /repositories/{id} → 404 | ✅ |
| 미인증 사용자 /repositories/{id} → 302 /login | ✅ |
