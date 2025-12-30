# 🚀 PR Review Server 배포 가이드

## 📋 사전 준비

### 1. API 키 준비
- **Anthropic API Key**: https://console.anthropic.com
- **GitHub Token**: https://github.com/settings/tokens
  - 필요 권한: `repo`, `read:org`

### 2. Docker 설치
- 로컬: Docker Desktop 설치
- 서버: Docker & Docker Compose 설치

---

## 🏗️ 로컬에서 빌드 & 푸시

### 1. 테스트 & 빌드 & 푸시

```bash
# 권한 부여
chmod +x deploy.sh

# 배포 실행
./deploy.sh
```

이 스크립트는:
1. ✅ 테스트 실행
2. ✅ Docker 이미지 빌드 (`smdmim/pr-review:latest`)
3. ✅ Docker Hub에 푸시

---

## 🖥️ 서버에 배포

### 1. 파일 준비

서버에 다음 파일들 업로드:

**docker-compose.yml** (이미 있음)

**.env** (새로 생성)
```bash
DOCKER_IMAGE=smdmim/pr-review:latest
PORT=8080
ANTHROPIC_API_KEY=sk-ant-api03-your-actual-key
GITHUB_TOKEN=ghp_your-actual-token
```

### 2. 실행

```bash
# 이미지 다운로드
docker-compose pull

# 백그라운드 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f
```

### 3. 확인

```bash
# Health check
curl http://localhost:8080/api/webhook/health

# 응답: "PR Review Server is running"
```

---

## 🔄 업데이트

```bash
# 1. 로컬에서 새 이미지 푸시
./deploy.sh

# 2. 서버에서 업데이트
docker-compose pull
docker-compose up -d
```

---

## 🌐 GitHub Webhook 설정

### 1. 서버 URL 확인
- `http://YOUR_SERVER_IP:8080` 또는
- `https://your-domain.com` (도메인 사용시)

### 2. GitHub 설정
1. 저장소 → **Settings** → **Webhooks** → **Add webhook**
2. **Payload URL**: `http://YOUR_SERVER_IP:8080/api/webhook/github/pr`
3. **Content type**: `application/json`
4. **Events**: `Pull requests` 선택
5. **Active** 체크
6. **Add webhook**

### 3. 테스트
- 테스트 PR 생성
- 서버 로그 확인: `docker-compose logs -f`

---

## 📊 유용한 명령어

```bash
# 상태 확인
docker-compose ps

# 로그 보기
docker-compose logs -f

# 재시작
docker-compose restart

# 중지
docker-compose down

# 중지 & 삭제
docker-compose down -v
```

---

## 🔧 포트 변경

**.env 파일 수정:**
```bash
PORT=9000  # 원하는 포트
```

**재시작:**
```bash
docker-compose down
docker-compose up -d
```

---

## 🔍 트러블슈팅

### 컨테이너가 시작되지 않음
```bash
# 로그 확인
docker-compose logs

# 환경 변수 확인
docker-compose config
```

### API 키 오류
```bash
# .env 파일 확인
cat .env

# 키가 제대로 설정되었는지 확인
docker-compose exec pr-review-server env | grep API
```

### 포트 충돌
```bash
# 포트 사용 중 확인
netstat -tulpn | grep 8080

# .env에서 다른 포트로 변경
```

---

## 📁 서버 파일 구조

```
/your/deploy/directory/
├── docker-compose.yml
└── .env
```

간단! 🎉

---

## 🎯 Quick Start

**로컬에서:**
```bash
./deploy.sh
```

**서버에서:**
```bash
# .env 파일 생성
cat > .env << EOF
DOCKER_IMAGE=smdmim/pr-review:latest
PORT=8080
ANTHROPIC_API_KEY=sk-ant-api03-xxx
GITHUB_TOKEN=ghp_xxx
EOF

# 실행
docker-compose pull
docker-compose up -d

# 확인
curl http://localhost:8080/api/webhook/health
```

완료! 🚀
