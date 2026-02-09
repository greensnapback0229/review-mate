# F15: Web UI Auth - SPEC

## 개요
Spring + Thymeleaf 기반 인증 관련 웹 페이지를 구현한다.
로그인, 회원가입(GitHub OAuth), 프로필 페이지를 포함.

## 상태: 미구현

## 관련 파일 (예정)
- `WebController.java` - 페이지 라우팅 컨트롤러
- `templates/login.html` - 로그인 페이지
- `templates/profile.html` - 프로필 페이지
- `templates/layout/` - 공통 레이아웃

## 범위 정의

### In-Scope
- 로그인 페이지 (GitHub 로그인 버튼)
- 로그인 성공 후 대시보드 redirect
- 프로필 페이지 (GitHub 정보 표시, 플랜 정보)
- 공통 레이아웃 (네비게이션, 헤더, 푸터)
- 반응형 디자인 (모바일 지원)

### Out-of-Scope
- 이메일/비밀번호 폼
- 프로필 사진 업로드 (GitHub avatar 사용)
- 다크 모드

## 의존성
- **의존**: F10 (user-auth) → Spring Security + OAuth
- **의존**: Thymeleaf, Bootstrap 5 (또는 Tailwind CSS)
- **피의존**: F16 (web-ui-dashboard), F17 (web-ui-settings)

## 상세 설계

### 페이지 목록

| URL | 템플릿 | 인증 필요 | 설명 |
|-----|--------|-----------|------|
| `/login` | `login.html` | X | GitHub 로그인 버튼 |
| `/profile` | `profile.html` | O | 사용자 프로필 |
| `/` | redirect | - | → /login 또는 /dashboard |

### 공통 레이아웃
```
templates/
  layout/
    base.html          -- 공통 레이아웃 (head, nav, footer)
    fragments/
      navbar.html      -- 네비게이션 바
      footer.html      -- 푸터
  login.html           -- 로그인
  profile.html         -- 프로필
  dashboard.html       -- (F16)
  settings.html        -- (F17)
```

### 네비게이션 구조
```
[로고] PR Review    [Dashboard] [Repositories] [Settings] [프로필 아이콘 ▼]
                                                            ├─ Profile
                                                            ├─ Plan
                                                            └─ Logout
```

### CSS 프레임워크
- **Bootstrap 5** (CDN) - 빠른 프로토타이핑, Spring + Thymeleaf와 궁합 좋음
- WebJars로 관리: `org.webjars:bootstrap:5.3.x`

## 에러 처리 정책

| 상황 | 동작 | 영향 |
|------|------|------|
| OAuth 로그인 실패 | /login?error 페이지 + 에러 메시지 | 재시도 안내 |
| 세션 만료 | /login redirect + "세션 만료" 메시지 | 재로그인 |
| 500 에러 | error.html 에러 페이지 | 사용자에게 안내 |

## 테스트 케이스
1. /login 접근 → 로그인 페이지 렌더링
2. GitHub 로그인 성공 → /dashboard redirect
3. /profile 미인증 접근 → /login redirect
4. /profile 인증 접근 → GitHub 정보 표시
5. 로그아웃 → /login redirect

## 완료 조건
- [ ] 공통 레이아웃 (base.html, navbar, footer)
- [ ] 로그인 페이지 (GitHub OAuth 버튼)
- [ ] 프로필 페이지
- [ ] 에러 페이지 (404, 500)
- [ ] 반응형 디자인
- [ ] Bootstrap 5 WebJars 설정
