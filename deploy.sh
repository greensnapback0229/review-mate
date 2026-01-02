#!/bin/bash

# PR Review Server 배포 스크립트
# Docker Hub에 이미지 푸시까지만 수행

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 설정
DOCKER_IMAGE="smdmim/pr-review"
DOCKER_TAG="latest"

echo -e "${GREEN}🚀 PR Review Server 배포 시작${NC}"

# 1. 테스트 실행
echo -e "\n${YELLOW}1️⃣ 테스트 실행${NC}"
./gradlew test
echo -e "${GREEN}✅ 테스트 통과${NC}"

# 2. Docker buildx 설정 (멀티 플랫폼 빌드)
echo -e "\n${YELLOW}2️⃣ Docker buildx 설정${NC}"
docker buildx create --use --name multiarch-builder --driver docker-container || docker buildx use multiarch-builder
echo -e "${GREEN}✅ Buildx 설정 완료${NC}"

# 3. Docker Hub에 로그인
echo -e "\n${YELLOW}3️⃣ Docker Hub 로그인${NC}"
docker login
echo -e "${GREEN}✅ 로그인 완료${NC}"

# 4. 멀티 플랫폼 이미지 빌드 & 푸시
echo -e "\n${YELLOW}4️⃣ Docker 멀티 플랫폼 빌드 & 푸시 (linux/amd64, linux/arm64)${NC}"
docker buildx build --platform linux/amd64,linux/arm64 \
  -t ${DOCKER_IMAGE}:${DOCKER_TAG} \
  --push \
  .
echo -e "${GREEN}✅ 이미지 빌드 & 푸시 완료${NC}"

# 완료
echo -e "\n${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}🎉 배포 완료!${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "\n${YELLOW}📦 이미지:${NC} ${GREEN}${DOCKER_IMAGE}:${DOCKER_TAG}${NC}"
echo -e "${YELLOW}🔗 Docker Hub:${NC} ${GREEN}https://hub.docker.com/r/smdmim/pr-review${NC}"
echo -e "${YELLOW}🏗️  플랫폼:${NC} ${GREEN}linux/amd64, linux/arm64${NC}"
