# DECISION LOG - F12: Repository Management

---

## DL-01: Organization 설치/레포 추가 시 사용자 매핑 전략

**날짜**: 2026-02-20
**상태**: 확정 (1차 B안 → 2차 A+B 복합으로 갱신)

### 컨텍스트

GitHub App을 organization에 설치하거나 org 레포를 추가할 때, Webhook payload의 `installation.account.id`는 **organization의 GitHub ID**로 전달된다. 이 값은 서비스 DB의 `users.github_id`에 존재하지 않아 레포 연결이 실패했다.

**1차 발생 로그 (`installation_repositories.added`)**:
```
WARN : User not found for github_id=146326836 during repositories_added, ignoring
```

1차 수정(B안)으로 `handleRepositoriesAdded`에서 `installationId` 역조회 fallback을 추가했으나, **org-only 설치(레포 없이 App만 설치)** 시나리오에서 2차 문제가 발생했다.

**2차 발생 로그 (`installation.created` → OAuth 로그인)**:
```
# installation.created: org account.id(146326836)로 pending 저장
INFO  : Saved to pending_installations (github_id=146326836, installationId=12345)

# 사용자가 개인 계정(24725356)으로 OAuth 로그인
INFO  : OAuth2 login: githubId=24725356
# pending 조회 실패 — org ID(146326836)와 개인 ID(24725356)가 불일치
WARN  : No pending installation found for githubId=24725356
```

`installation.created` 시점에도 `account.id` = org ID로 pending을 저장하므로, 사용자가 개인 계정으로 로그인해도 pending을 찾지 못해 레포 연결이 이루어지지 않는다. `installationId` 역조회 역시 `user_repositories`가 아직 비어 있어 실패한다.

### 고려한 옵션

**A. Webhook payload의 `sender` 필드 활용**

`installation.created`와 `installation_repositories.added` 이벤트 모두 `sender` 필드에 실제 액션을 수행한 사람의 개인 GitHub ID가 포함된다. account.type이 "Organization"일 때 `sender.id`를 사용자 조회 및 pending 저장에 사용한다.

- 장점: 직관적이고 GitHub 표준에 부합. `installation.created`의 pending 저장 문제까지 근본적으로 해결.
- 단점: `InstallationWebhookPayload`에 `Sender` 클래스 추가 필요. organization admin이 다른 사람 대신 레포를 추가하는 케이스에서 sender가 서비스 사용자와 다를 수 있음(드문 케이스).

**B. `installationId`로 기존 `user_repositories` 역조회**

`handleRepositoriesAdded`에서 `account.id` 조회 실패 시 `installationId`로 `user_repositories`를 역조회하여 userId를 찾는다.

- 장점: 이미 `user_repositories`에 레코드가 있는 경우(레포를 나중에 추가하는 케이스) 별도 DTO 변경 없이 동작.
- 단점: `installation.created` 단계의 pending 저장 문제를 해결하지 못함. `user_repositories`가 비어 있으면 역조회 자체가 실패함.

**C. 변경 없이 pending 저장 후 처리 위임**

`account.id`(org ID)로 pending을 저장하고 사용자가 다음 로그인 시 연결을 시도한다.

- 단점: 개인 `githubId`로 로그인하면 pending 조회가 불일치하여 자동 연결이 동작하지 않는다. 근본적으로 해결 불가.

### 결정: **A+B 복합 방식**

단독 B안은 `installation.created` 단계의 pending 저장 문제를 해결하지 못함이 실 운영에서 확인되었다. A안을 주 전략으로 채택하고, B안을 보조 fallback으로 유지한다.

**구현**:
- `InstallationWebhookPayload`에 `Sender` 내부 클래스(`id`, `login`) 추가
- `resolveInstallerGithubId()` 헬퍼: `account.type == "Organization"` && `sender != null`이면 `sender.id` 반환, 그 외엔 `account.id` 반환
- `handleCreated`와 `handleRepositoriesAdded` 모두 `resolveInstallerGithubId()`를 통해 조회 ID 결정

**최종 fallback 순서 (`handleRepositoriesAdded`)**:
1. `resolveInstallerGithubId()`(sender.id 또는 account.id)로 사용자 직접 조회
2. 실패 시 `installationId`로 `user_repositories` 역조회 (이미 연결된 레포가 있는 케이스)
3. 그것도 없으면 `resolveInstallerGithubId()`를 githubId로 `pending_installations`에 저장

**최종 fallback 순서 (`handleCreated`)**:
1. `resolveInstallerGithubId()`로 사용자 직접 조회
2. 없으면 `resolveInstallerGithubId()`를 githubId로 `pending_installations`에 저장

### 재검토 조건

- organization admin이 본인과 다른 서비스 사용자의 레포를 추가하는 케이스가 빈번해지면 sender 방식의 한계가 드러날 수 있다. 이 경우 GitHub API를 통한 installation 정보 조회나 명시적 사용자 선택 UI 도입을 검토한다.
- 하나의 installationId에 여러 userId가 연결되는 시나리오(팀 공유 설치 등)가 도입되면 `findFirstByInstallationId` 대신 명시적 사용자 선택 로직이 필요하다.
