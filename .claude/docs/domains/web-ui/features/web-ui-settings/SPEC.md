# F17: Web UI Settings - SPEC

## 개요
Repository 설정, Feature Registry 편집, 플랜 관리를 위한
설정 페이지를 구현한다.

## 상태: 미구현

## 관련 파일 (예정)
- `SettingsController.java` - 설정 페이지 컨트롤러
- `templates/settings/` - 설정 관련 템플릿
- `FeatureRegistryEditorService.java` - Feature Registry 편집 서비스

## 범위 정의

### In-Scope
- Repository 설정 페이지 (리뷰 활성/비활성, 리뷰 언어, 심각도 필터)
- Feature Registry YAML 편집기 (웹 기반)
- 플랜 관리 페이지 (현재 플랜, 사용량, 업그레이드/다운그레이드)
- 일반 설정 (알림 설정)

### Out-of-Scope
- YAML 문법 자동 완성
- Feature Registry 시각적 편집기 (드래그 앤 드롭)
- 실시간 미리보기

## 의존성
- **의존**: F6 (review-customization) → config.yml 설정 시스템
- **의존**: F12 (repository-management) → Repository 목록
- **의존**: F14 (pricing-plans) → 플랜 정보 + Stripe
- **의존**: F15 (web-ui-auth) → 공통 레이아웃

## 상세 설계

### 페이지 목록

| URL | 템플릿 | 설명 |
|-----|--------|------|
| `/settings` | `settings/index.html` | 설정 메인 (일반) |
| `/settings/repositories/{id}` | `settings/repo-config.html` | Repository별 설정 |
| `/settings/repositories/{id}/features` | `settings/feature-editor.html` | Feature Registry 편집 |
| `/settings/plan` | `settings/plan.html` | 플랜 관리 |

### Repository 설정 페이지
```
┌─ Repository 설정: owner/repo1 ──────────────────┐
│                                                    │
│  리뷰 활성화: [✅ ON / OFF]                        │
│                                                    │
│  리뷰 언어:  [한국어 ▼]                             │
│  심각도 필터: [전체 ▼]  (Critical / Major / All)    │
│  최대 인라인 코멘트: [10]                           │
│                                                    │
│  무시할 파일 패턴:                                  │
│  ┌──────────────────────────────────────────┐      │
│  │ **/*.test.java                            │      │
│  │ **/*.md                                   │      │
│  │ **/generated/**                           │      │
│  └──────────────────────────────────────────┘      │
│  [+ 패턴 추가]                                     │
│                                                    │
│  커스텀 리뷰 지침:                                  │
│  ┌──────────────────────────────────────────┐      │
│  │ - 변수명은 camelCase를 사용해야 합니다     │      │
│  │ - 모든 public 메서드에 Javadoc 필요        │      │
│  └──────────────────────────────────────────┘      │
│                                                    │
│  [저장] [Feature Registry 편집 →]                  │
└──────────────────────────────────────────────────┘
```

### Feature Registry 편집기
```
┌─ Feature Registry: owner/repo1 ─────────────────┐
│                                                    │
│  ┌─ YAML 편집기 ───────────────────────────────┐  │
│  │ features:                                     │  │
│  │   - name: AUTH                                │  │
│  │     description: 인증/인가 기능               │  │
│  │     patterns:                                 │  │
│  │       - "src/auth/**"                         │  │
│  │     coreFiles:                                │  │
│  │       - "src/auth/AuthService.java"           │  │
│  │     reviewFocus:                              │  │
│  │       - "보안 취약점 확인"                     │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  [YAML 검증] [저장] [GitHub에 커밋]                │
└──────────────────────────────────────────────────┘
```

**YAML 편집기 구현**:
- CodeMirror 또는 Monaco Editor (YAML 문법 하이라이팅)
- 저장 시 YAML 파싱 검증
- "GitHub에 커밋" → GitHub API로 feature-registry.yml 직접 커밋

### 플랜 관리 페이지
```
┌─ 플랜 관리 ─────────────────────────────────────┐
│                                                    │
│  현재 플랜: FREE                                   │
│  이번 달 사용량: 18 / 30 리뷰                      │
│  ████████████████░░░░░ 60%                        │
│                                                    │
│  ┌─ PRO 플랜 ($15/월) ──────────────────────┐     │
│  │ ✓ 무제한 리뷰                              │     │
│  │ ✓ 무제한 Repository                        │     │
│  │ ✓ 무제한 Feature Memory                    │     │
│  │ ✓ 댓글 응답 무제한                          │     │
│  │                                            │     │
│  │ [PRO로 업그레이드 →]                        │     │
│  └────────────────────────────────────────────┘    │
│                                                    │
│  결제 이력:                                        │
│  │ 2025-01 │ $15.00 │ ✅ 결제 완료 │               │
│  │ 2024-12 │ $15.00 │ ✅ 결제 완료 │               │
└──────────────────────────────────────────────────┘
```

## 에러 처리 정책

| 상황 | 동작 | 영향 |
|------|------|------|
| YAML 파싱 실패 | 에러 메시지 표시 + 저장 거부 | 잘못된 설정 방지 |
| GitHub 커밋 실패 | 에러 메시지 + 재시도 안내 | 설정 미반영 |
| 타인의 Repository 접근 | 403 에러 | 접근 차단 |
| Stripe 결제 페이지 오류 | 에러 메시지 + 재시도 | 플랜 변경 불가 |

## 테스트 케이스
1. Repository 설정 저장 → config.yml 업데이트
2. Feature Registry YAML 편집 → 파싱 검증
3. YAML 문법 오류 → 에러 표시 + 저장 거부
4. 플랜 업그레이드 → Stripe Checkout redirect
5. 타인의 Repository 설정 → 403
6. 무시 패턴 추가/삭제 → 정상 반영

## 완료 조건
- [ ] Repository 설정 페이지 (6개 설정 항목)
- [ ] Feature Registry YAML 편집기 (CodeMirror)
- [ ] YAML 검증 + GitHub 커밋 기능
- [ ] 플랜 관리 페이지 (현재 플랜, 사용량, 업그레이드)
- [ ] 결제 이력 표시
- [ ] 반응형 디자인
- [ ] 단위 테스트 6개 이상
