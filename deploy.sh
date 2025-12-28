#!/bin/bash

# PR Review Server 배포 스크립트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 설정
DOCKER_IMAGE="smdmim/pr-review"
DOCKER_TAG="latest"
DOCKER_HUB_USERNAME="smdmim"

echo -e "${GREEN}🚀 PR Review Server 배포 시작${NC}"

# 1. 환경 변수 확인
echo -e "\n${YELLOW}1️⃣ 환경 변수 확인${NC}"
if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo -e "${RED}❌ ANTHROPIC_API_KEY가 설정되지 않았습니다${NC}"
    exit 1
fi

if [ -z "$GITHUB_TOKEN" ]; then
    echo -e "${RED}❌ GITHUB_TOKEN이 설정되지 않았습니다${NC}"
    exit 1
fi

if [ -z "$DOCKER_HUB_USERNAME" ] || [ "$DOCKER_HUB_USERNAME" = "smdmim" ]; then
    echo -e "${GREEN}✅ Docker Hub: smdmim/pr-review${NC}"
fi

echo -e "${GREEN}✅ 환경 변수 확인 완료${NC}"

# 2. 테스트 실행
echo -e "\n${YELLOW}2️⃣ 테스트 실행${NC}"
./gradlew test
echo -e "${GREEN}✅ 테스트 통과${NC}"

# 3. Docker 이미지 빌드
echo -e "\n${YELLOW}3️⃣ Docker 이미지 빌드${NC}"
docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} .
echo -e "${GREEN}✅ 이미지 빌드 완료${NC}"

# 4. Docker Hub에 태그
echo -e "\n${YELLOW}4️⃣ Docker Hub 태그 설정${NC}"
docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE}:${DOCKER_TAG}
echo -e "${GREEN}✅ 태그 설정 완료${NC}"

# 5. Docker Hub에 푸시
echo -e "\n${YELLOW}5️⃣ Docker Hub에 푸시${NC}"
echo "Docker Hub에 로그인하세요:"
docker login

docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
echo -e "${GREEN}✅ 이미지 푸시 완료${NC}"

# 6. 배포 정보 출력
echo -e "\n${GREEN}🎉 배포 완료!${NC}"
echo -e "\n${YELLOW}서버에서 실행 명령어:${NC}"
echo -e "${GREEN}docker pull ${DOCKER_IMAGE}:${DOCKER_TAG}${NC}"
echo -e "${GREEN}docker run -d \\
  --name pr-review-server \\
  -p 8080:8080 \\
  -e ANTHROPIC_API_KEY=your-key \\
  -e GITHUB_TOKEN=your-token \\
  ${DOCKER_IMAGE}:${DOCKER_TAG}${NC}"

echo -e "\n${YELLOW}또는 docker-compose 사용:${NC}"
echo -e "${GREEN}docker-compose up -d${NC}"
