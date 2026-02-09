# F3: Webhook Security - SPEC

## 개요
GitHub Webhook Secret을 사용한 HMAC-SHA256 서명 검증을 구현하여,
인증되지 않은 요청이 리뷰 파이프라인을 트리거하는 것을 방지한다.

## 현재 상태
- `application.yml`에 `github.webhook.secret` 설정 존재
- `WebhookController`에서 검증 로직 **미구현**
- 현재 누구나 `/api/webhook/github/pr` 엔드포인트를 호출 가능

## 범위

### In-Scope
- `X-Hub-Signature-256` 헤더를 사용한 HMAC-SHA256 검증
- 검증 실패 시 `401 Unauthorized` 응답
- Webhook Secret 미설정 시 경고 로그 + 검증 스킵 (개발 편의)

### Out-of-Scope
- IP 화이트리스트 (GitHub IP ranges)
- Rate limiting
- Replay attack 방지 (타임스탬프 검증)

## 상세 동작

### 검증 플로우
```
Request → X-Hub-Signature-256 헤더 추출
       → Raw body로 HMAC-SHA256 계산
       → 헤더 값과 비교 (timing-safe comparison)
       → 일치하면 처리, 불일치면 401
```

### 구현 방식
- Spring `HandlerInterceptor` 또는 `Filter`로 구현
- Raw request body를 읽어야 하므로 `ContentCachingRequestWrapper` 사용
- `@Value("${github.webhook.secret}")` 로 secret 주입

### 시그니처 계산
```
HMAC-SHA256(webhook_secret, raw_body) → hex digest
Expected header: "sha256={hex_digest}"
```

## 수정 대상 파일
- **신규**: `WebhookSignatureFilter.java` (또는 Interceptor)
- **수정**: `WebhookController.java` - 필요시 raw body 처리
- **수정**: `application.yml` - secret 설정 확인

## 테스트 케이스
1. 유효한 시그니처 → 200 OK + 정상 처리
2. 잘못된 시그니처 → 401 Unauthorized
3. 시그니처 헤더 없음 → 401 Unauthorized
4. Webhook Secret 미설정 → 경고 로그 + 검증 스킵
5. 빈 body → 적절한 에러 응답

## 완료 조건
- [ ] HMAC-SHA256 검증 필터/인터셉터 구현
- [ ] 검증 실패 시 401 응답
- [ ] 단위 테스트 4개 이상 통과
- [ ] 기존 기능 정상 동작 확인
