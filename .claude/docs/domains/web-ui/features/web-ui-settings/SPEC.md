# F17: web-ui-settings SPEC

## 상태: **완료**

## 목표
저장소별 설정 페이지(활성/비활성 토글) 및 Feature Registry 조회 페이지를 제공한다.
F14(pricing-plans) 제거 → 플랜 관리 없음. F6(review-customization) 미완료 → 복잡한 리뷰 설정 없음.

## 의존성
- F12 (repository-management): UserRepository, UserRepositoryJpaRepository
- F15 (web-ui-auth): layout/base.html, 인증 흐름
- F16 (web-ui-dashboard): 브레드크럼 연동

## 라우트

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | `/settings/repositories/{repositoryId}` | 저장소 설정 페이지 | 필요 |
| GET | `/settings/repositories/{repositoryId}/features` | Feature Registry 조회 | 필요 |
| PUT | `/api/settings/repositories/{repositoryId}/active` | 활성/비활성 토글 API | 필요 |

## 저장소 설정 (`/settings/repositories/{repositoryId}`)

### 접근 제어
- `userRepositoryJpaRepository.findByUserIdAndRepositoryId(userId, repositoryId)` 로 소유권 검증
- 미소유 저장소 → HTTP 404
- 비활성 저장소도 설정 페이지 접근 가능 (active 여부 무관)

### Model 데이터
| 속성 | 타입 | 출처 |
|------|------|------|
| `repository` | `UserRepository` | JPA 직접 조회 |

### UI 구성
- 저장소 정보 테이블 (이름, ID, installationId, 연결일)
- isActive 토글 스위치 (AJAX → `PUT /api/settings/repositories/{id}/active`)
- Feature Registry 링크 + GitHub 편집 링크
- 위험 구역: GitHub App 설치 관리 링크

## Feature Registry 조회 (`/settings/repositories/{repositoryId}/features`)

### 동작
- `FeatureRegistryLoader.loadFromRepository(repoFullName, null)` 호출
- 성공: 파싱된 Feature 카드 목록 표시
- 실패(파일 없음 등): `loadError` 메시지 + 파일 생성 GitHub 링크

### Model 데이터
| 속성 | 타입 | 출처 |
|------|------|------|
| `repository` | `UserRepository` | JPA 조회 |
| `features` | `Map<String, FeatureDefinition>` | FeatureRegistryLoader |
| `loadError` | `String` or `null` | 예외 발생 시 |

## 토글 API (`PUT /api/settings/repositories/{repositoryId}/active`)

- Request body: `{"active": true/false}`
- `UserRepositoryService.toggleActive(userId, repositoryId, active)` 호출
- 성공: 200 `{"repositoryId": ..., "active": ..., "message": "..."}`
- 미소유: 404

## 구현 파일
- `web/WebPageController.java` — repositorySettings / repositoryFeatures 추가
- `web/RepositorySettingsController.java` — PUT 토글 API
- `tenant/UserRepositoryService.java` — toggleActive() 추가
- `templates/settings/repositories/detail.html`
- `templates/settings/repositories/features.html`

## 테스트

### WebPageControllerTest
| 케이스 | 결과 |
|--------|------|
| 인증 사용자 /settings/repositories/{id} → 설정 페이지 + repository 모델 | ✅ |
| 타인 저장소 /settings/repositories/{id} → 404 | ✅ |
| 미인증 /settings/repositories/{id} → 302 | ✅ |
| Feature Registry 정상 로드 → features 모델 포함 | ✅ |
| Feature Registry 파일 없음 → loadError 모델 포함 | ✅ |
| 미인증 /features → 302 | ✅ |

### RepositorySettingsControllerTest
| 케이스 | 결과 |
|--------|------|
| PUT active=true → 200 + active=true | ✅ |
| PUT active=false → 200 + active=false | ✅ |
| 미소유 저장소 → 404 | ✅ |
| 미인증 → 302 | ✅ |

## 범위 외 (Out-of-Scope)
- YAML 직접 편집/GitHub 커밋 (F6 구현 후 검토)
- 플랜 관리 (F14 제거됨)
- 리뷰 언어/심각도 필터 설정 (F6 의존)
