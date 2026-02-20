# DECISION LOG - F12: Repository Management

---

## DL-01: Organization 레포 추가 시 사용자 매핑 전략

**날짜**: 2026-02-20
**상태**: 확정

### 컨텍스트

`installation_repositories.added` Webhook 이벤트에서 `installation.account.id`로 사용자를 조회하는데, GitHub App을 개인 계정으로 설치한 사용자가 자신이 속한 **organization의 레포지토리를 추가**하면 `account.id`가 organization의 GitHub ID로 전달된다. 이 ID는 서비스 DB의 `users.github_id`에 존재하지 않아 "User not found … ignoring"으로 처리되어 레포 연결이 실패한다.

실제 발생 로그:
```
WARN g.p.installation.InstallationHandler : User not found for github_id=146326836 during repositories_added, ignoring
```

`146326836`은 개인 사용자 ID가 아닌 organization의 GitHub ID이다.

### 고려한 옵션

**A. Webhook payload의 `sender` 필드 활용**

`installation_repositories` 이벤트의 `sender` 필드에는 레포를 추가하는 액션을 수행한 사람의 GitHub ID가 담긴다. 이를 `InstallationWebhookPayload`에 추가하고 우선 조회에 사용한다.

- 장점: 직관적이고 GitHub 표준에 부합
- 단점: organization admin이 다른 사람 대신 레포를 추가하는 경우 `sender`가 서비스 사용자와 다를 수 있음. DTO 변경 필요.

**B. `installationId`로 기존 `user_repositories` 역조회 (채택)**

`installation.created` 시점에 이미 해당 `installationId`와 userId가 `user_repositories` 테이블에 연결되어 있다. `handleRepositoriesAdded`에서 `account.id`로 조회 실패 시 동일한 `installationId`로 `user_repositories`를 역조회하여 userId를 찾는다.

- 장점: `installationId`는 GitHub App 설치 단위의 고유 ID로, 사용자 간 충돌이 불가능하다. 추가 DTO 변경 없이 기존 테이블만 활용한다. organization admin 케이스에서도 정확하게 설치한 사람을 찾는다.
- 단점: `user_repositories`에 `findFirstByInstallationId` 쿼리 메서드 추가 필요.

**C. 변경 없이 pending 저장 후 처리 위임**

`handleCreated`와 동일하게 pending에 저장하고, 사용자가 다음 로그인 시 자동 연결한다.

- 단점: 이미 가입한 사용자인데 pending → 재로그인이 필요하다. UX가 나쁘고 "즉시 연결"이 안 된다는 혼란을 준다.

### 결정: **B - `installationId` 역조회 fallback**

`installationId`는 GitHub이 App 설치 단위로 부여하는 전역 고유 값이므로, 동일 installationId를 가진 `user_repositories` 레코드는 반드시 해당 설치를 수행한 한 명의 사용자에게만 귀속된다. 따라서 역조회 결과가 다른 사용자와 겹칠 가능성이 없고, 별도 DTO 변경 없이 구현할 수 있다.

최종 fallback 순서:
1. `account.id`로 사용자 직접 조회 (개인 설치 케이스)
2. 실패 시 `installationId`로 `user_repositories` 역조회 (organization 레포 추가 케이스)
3. 그것도 없으면 `pending_installations`에 저장 (미가입 사용자 케이스)

### 재검토 조건

- 하나의 installationId에 여러 userId가 연결되는 시나리오(예: 팀 공유 설치)가 도입되면 `findFirstByInstallationId` 대신 명시적 사용자 선택 로직이 필요하다.
- organization admin이 본인과 다른 서비스 사용자의 레포를 추가하는 케이스가 빈번해지면 `sender` 방식(A)으로 전환을 검토한다.
