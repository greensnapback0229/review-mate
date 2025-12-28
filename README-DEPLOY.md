# 🚀 PR Review Server 배포 가이드

## 📋 사전 준비

### 1. Docker Hub 계정
- https://hub.docker.com 에서 계정 생성
- Docker Hub username 확인

### 2. API 키 준비
- Anthropic API Key: https://console.anthropic.com
- GitHub Personal Access Token: https://github.com/settings/tokens

---

## 🐳 Docker 빌드 & 배포

### 방법 1: 자동 배포 스크립트 (권장)

```bash
# 1. 환경 변수 설정
export ANTHROPIC_API_KEY=sk-ant-api03-xxx
export GITHUB_TOKEN=ghp_xxx
export DOCKER_HUB_USERNAME=your-dockerhub-username

# 2. 배포 스크립트 실행
chmod +x deploy.sh
./deploy.sh
```

### 방법 2: 수동 배포

```bash
# 1. 테스트
./gradlew test

# 2. Docker 이미지 빌드
docker build -t pr-review-server:latest .

# 3. Docker Hub에 태그
docker tag pr-review-server:latest YOUR_USERNAME/pr-review-server:latest

# 4. Docker Hub 로그인 & 푸시
docker login
docker push YOUR_USERNAME/pr-review-server:latest
```

---

## 🖥️ 서버에서 실행

### Option A: docker run

```bash
# 1. 이미지 다운로드
docker pull YOUR_USERNAME/pr-review-server:latest

# 2. 컨테이너 실행
docker run -d \
  --name pr-review-server \
  -p 8080:8080 \
  -e ANTHROPIC_API_KEY=sk-ant-api03-xxx \
  -e GITHUB_TOKEN=ghp_xxx \
  --restart unless-stopped \
  YOUR_USERNAME/pr-review-server:latest

# 3. 로그 확인
docker logs -f pr-review-server

# 4. Health check
curl http://localhost:8080/api/webhook/health
```

### Option B: docker-compose (권장)

```bash
# 1. .env 파일 생성
cat > .env << EOF
ANTHROPIC_API_KEY=sk-ant-api03-xxx
GITHUB_TOKEN=ghp_xxx
DOCKER_HUB_USERNAME=your-username
EOF

# 2. docker-compose.yml 수정
# image: pr-review-server:latest
# → image: YOUR_USERNAME/pr-review-server:latest

# 3. 실행
docker-compose up -d

# 4. 로그 확인
docker-compose logs -f

# 5. 중지
docker-compose down
```

---

## 🔧 로컬 테스트 (배포 전)

```bash
# 1. 로컬 빌드
docker build -t pr-review-server:latest .

# 2. 로컬 실행
docker run -d \
  --name pr-review-server-test \
  -p 8080:8080 \
  -e ANTHROPIC_API_KEY=your-key \
  -e GITHUB_TOKEN=your-token \
  pr-review-server:latest

# 3. 테스트
curl http://localhost:8080/api/webhook/health
curl http://localhost:8080/api/test/ping

# 4. 정리
docker stop pr-review-server-test
docker rm pr-review-server-test
```

---

## 🌐 GitHub Webhook 설정

### 1. 서버 URL 확인
- 서버 IP: `http://YOUR_SERVER_IP:8080`
- ngrok (테스트용): `https://xxx.ngrok.io`

### 2. GitHub 설정
1. 저장소 → Settings → Webhooks → Add webhook
2. Payload URL: `http://YOUR_SERVER_IP:8080/api/webhook/github/pr`
3. Content type: `application/json`
4. Events: `Pull requests` 선택
5. Active 체크
6. Add webhook

### 3. 테스트
- 테스트 PR 생성
- 서버 로그 확인: `docker logs -f pr-review-server`

---

## 📊 유용한 명령어

```bash
# 컨테이너 상태 확인
docker ps
docker ps -a

# 로그 실시간 확인
docker logs -f pr-review-server

# 컨테이너 내부 접속
docker exec -it pr-review-server /bin/bash

# 리소스 사용량 확인
docker stats pr-review-server

# 이미지 확인
docker images

# 컨테이너 재시작
docker restart pr-review-server

# 컨테이너 삭제
docker stop pr-review-server
docker rm pr-review-server
```

---

## 🔍 트러블슈팅

### 문제 1: 컨테이너가 시작 후 바로 종료됨
```bash
# 로그 확인
docker logs pr-review-server

# 일반적 원인:
# - API 키가 설정되지 않음
# - 포트가 이미 사용 중
```

### 문제 2: Health check 실패
```bash
# 컨테이너 상태 확인
docker inspect pr-review-server

# 포트 확인
netstat -tulpn | grep 8080
```

### 문제 3: GitHub Webhook이 도달하지 않음
```bash
# 방화벽 확인
sudo ufw status
sudo ufw allow 8080

# 서버 로그 확인
docker logs -f pr-review-server
```

---

## 🎯 Quick Start

```bash
# 1. 로컬에서 빌드 & 푸시
export DOCKER_HUB_USERNAME=your-username
export ANTHROPIC_API_KEY=your-key
export GITHUB_TOKEN=your-token
./deploy.sh

# 2. 서버에서 실행
docker pull your-username/pr-review-server:latest
docker run -d \
  --name pr-review-server \
  -p 8080:8080 \
  -e ANTHROPIC_API_KEY=your-key \
  -e GITHUB_TOKEN=your-token \
  --restart unless-stopped \
  your-username/pr-review-server:latest

# 3. 확인
curl http://localhost:8080/api/webhook/health
```

완료! 🎉
