# GitHub App 배포 가이드

## 📋 사전 준비

### 1. GitHub App 생성 완료
- App ID: `2602451`
- Installation ID: `102831206`
- Private Key: `review-mate-ai.2026-01-05.private-key.pem`

---

## 🚀 서버 배포 단계

### Step 1: 서버 접속
```bash
ssh user@srv.pr-review.cloud
cd /home/user/pr-review
```

---

### Step 2: secrets 디렉토리 생성
```bash
mkdir -p secrets
```

---

### Step 3: Private Key 업로드

**방법 1: SCP로 전송 (로컬에서)**
```bash
scp secrets/review-mate-ai.2026-01-05.private-key.pem \
    user@srv.pr-review.cloud:/home/user/pr-review/secrets/
```

**방법 2: 직접 생성 (서버에서)**
```bash
# 서버에서
nano secrets/review-mate-ai.2026-01-05.private-key.pem

# 로컬의 키 파일 내용을 복사해서 붙여넣기
# Ctrl+X → Y → Enter로 저장
```

---

### Step 4: 권한 설정 (중요!)
```bash
chmod 600 secrets/review-mate-ai.2026-01-05.private-key.pem

# 확인
ls -la secrets/
# 출력: -rw------- 1 user user 1675 Jan 06 15:00 review-mate-ai.2026-01-05.private-key.pem
```

---

### Step 5: .env 파일 업데이트
```bash
nano .env
```

**필수 환경 변수**:
```bash
# Anthropic API
ANTHROPIC_API_KEY=sk-ant-api03-xxx

# GitHub App Configuration
GITHUB_APP_ID=2602451
GITHUB_APP_INSTALLATION_ID=102831206
GITHUB_APP_PRIVATE_KEY_PATH=secrets/review-mate-ai.2026-01-05.private-key.pem
GITHUB_WEBHOOK_SECRET=9dc60fc4a06d9a7eb28ccd1f2a683f469820d5b6ef749c222463c18b850b09e6

# Server
PORT=44001
DOCKER_IMAGE=smdmim/pr-review:latest
```

---

### Step 6: docker-compose.yml 업데이트
**docker-compose.yml 내용**:
```yaml
version: '3.8'

services:
  pr-review-server:
    image: ${DOCKER_IMAGE}
    container_name: pr-review-server
    ports:
      - "${PORT}:8080"
    env_file:
      - .env
    volumes:
      # Private Key 마운트 (중요!)
      - ./secrets:/app/secrets:ro
    restart: unless-stopped
```

---

### Step 7: 배포 실행
```bash
# 최신 이미지 다운로드
docker-compose pull

# 기존 컨테이너 중지 및 제거
docker-compose down

# 새 컨테이너 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f
```

---

## ✅ 배포 확인

### 로그에서 확인할 내용
```
✅ 성공:
INFO ... GitHubAppAuthenticator : Generating new installation token
INFO ... GitHubAppAuthenticator : Installation token generated successfully
INFO ... GitHubConfig            : GitHub App client initialized successfully

❌ 실패:
ERROR ... No such file or directory
→ secrets 폴더 마운트 확인

ERROR ... Injection of autowired dependencies failed
→ 환경 변수 확인
```

---

## 🔍 트러블슈팅

### 에러 1: "No such file or directory"
**원인**: Private Key 파일이 없음

**해결**:
```bash
# 파일 존재 확인
ls -la secrets/review-mate-ai.2026-01-05.private-key.pem

# 없으면 다시 업로드
scp secrets/review-mate-ai.2026-01-05.private-key.pem user@srv:~/pr-review/secrets/
```

---

### 에러 2: "Permission denied"
**원인**: 파일 권한 문제

**해결**:
```bash
chmod 600 secrets/review-mate-ai.2026-01-05.private-key.pem
chown user:user secrets/review-mate-ai.2026-01-05.private-key.pem
```

---

### 에러 3: "Cannot connect to the Docker daemon"
**원인**: Docker가 실행 중이지 않음

**해결**:
```bash
sudo systemctl start docker
sudo systemctl enable docker
```

---

## 📊 최종 디렉토리 구조

```
/home/user/pr-review/
├── .env                    # 환경 변수
├── docker-compose.yml      # Docker 설정
└── secrets/                # Private Key (Git 제외)
    └── review-mate-ai.2026-01-05.private-key.pem
```

---

## 🎉 완료 테스트

1. **Health Check**:
```bash
curl http://localhost:44001/actuator/health
```

2. **PR 생성**:
- pr-server-test 레포에 PR 생성
- 5초 후 `review-mate-ai[bot]` 코멘트 확인

---

## 🔐 보안 주의사항

1. **Private Key는 절대 Git에 올리지 말 것**
   - `.gitignore`에 `secrets/`, `*.pem` 포함됨

2. **권한 설정**
   - Private Key: `600` (소유자만 읽기)
   - secrets 폴더: `700` (소유자만 접근)

3. **환경 변수 보호**
   - `.env` 파일도 Git에서 제외
   - 서버에만 존재해야 함

---

## 📝 참고 링크

- GitHub App 설정: https://github.com/settings/apps/review-mate-ai
- Docker Hub: https://hub.docker.com/r/smdmim/pr-review
- 서버: https://srv.pr-review.cloud
