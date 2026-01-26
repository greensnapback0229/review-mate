package greensnaback0229.pr_review_server.github;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.FileReader;
import java.io.IOException;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GitHub App 인증을 처리하는 클래스
 * 
 * 동작 방식:
 * 1. Private Key로 JWT 생성
 * 2. Repository로부터 Installation 자동 탐지
 * 3. Installation Access Token 발급 (Repository별로 자동)
 * 4. Token은 1시간 유효 (자동 갱신 및 캐싱)
 */
@Slf4j
@Component
public class GitHubAppAuthenticator {
    
    @Value("${github.app.id}")
    private String appId;
    
    @Value("${github.app.private-key-path}")
    private String privateKeyPath;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    // Repository별 토큰 캐시 (repoFullName -> TokenCache)
    private final Map<String, TokenCache> tokenCacheMap = new ConcurrentHashMap<>();
    
    /**
     * Token 캐시 정보
     */
    private static class TokenCache {
        String token;
        Instant expiresAt;
        
        TokenCache(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
        
        boolean isValid() {
            // 5분 여유를 두고 만료 체크
            return token != null && expiresAt != null 
                && Instant.now().isBefore(expiresAt.minusSeconds(300));
        }
    }
    
    /**
     * Repository로부터 자동으로 Installation을 찾아서 Token 발급
     * 
     * @param repoFullName Repository 전체 이름 (예: "owner/repo")
     * @return Installation Access Token
     * @throws IOException GitHub API 호출 실패 시
     */
    public String getInstallationToken(String repoFullName) throws IOException {
        // 캐시된 토큰이 유효하면 재사용
        TokenCache cached = tokenCacheMap.get(repoFullName);
        if (cached != null && cached.isValid()) {
            log.debug("Using cached installation token for {}", repoFullName);
            return cached.token;
        }
        
        log.info("Generating new installation token for repository: {}", repoFullName);
        
        // 1. JWT 생성
        String jwt = generateJWT();
        
        // 2. Repository의 Installation ID 조회
        String url = String.format("https://api.github.com/repos/%s/installation", repoFullName);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("Authorization", "Bearer " + jwt);
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> installationResponse = restTemplate.exchange(
            url, HttpMethod.GET, entity, Map.class
        );
        
        if (installationResponse.getStatusCode() != HttpStatus.OK) {
            throw new IOException(
                "GitHub App is not installed on repository: " + repoFullName + ". " +
                "Please install the app on this repository or organization."
            );
        }
        
        Map<String, Object> installationData = installationResponse.getBody();
        int installationId = (Integer) installationData.get("id");
        
        log.info("Found installation ID: {} for repository: {}", installationId, repoFullName);
        
        // 3. Installation Token 발급
        String tokenUrl = String.format(
            "https://api.github.com/app/installations/%d/access_tokens",
            installationId
        );
        
        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenUrl, entity, Map.class);
        
        if (tokenResponse.getStatusCode() != HttpStatus.CREATED) {
            throw new RuntimeException("Failed to get installation token: " + tokenResponse.getStatusCode());
        }
        
        Map<String, Object> tokenData = tokenResponse.getBody();
        String token = (String) tokenData.get("token");
        String expiresAt = (String) tokenData.get("expires_at");
        Instant expiresAtInstant = Instant.parse(expiresAt);
        
        // 4. 캐시에 저장
        tokenCacheMap.put(repoFullName, new TokenCache(token, expiresAtInstant));
        
        log.info("Installation token generated successfully for {}", repoFullName);
        log.debug("Token (first 20 chars): {}...", 
            token.substring(0, Math.min(20, token.length())));
        log.info("Token expires at: {}", expiresAt);
        
        return token;
    }
    
    /**
     * 하위 호환성을 위한 메서드 (deprecated)
     * 기존 코드에서 Installation ID 없이 호출하는 경우
     * 
     * @deprecated getInstallationToken(String repoFullName) 사용 권장
     */
    @Deprecated
    public String getInstallationToken() throws IOException {
        log.warn("getInstallationToken() called without repository name. " +
            "This method is deprecated. Please use getInstallationToken(String repoFullName)");
        throw new UnsupportedOperationException(
            "Installation token requires repository name. " +
            "Use getInstallationToken(String repoFullName) instead."
        );
    }
    
    /**
     * Private Key로 JWT 생성
     * GitHub App으로 인증하기 위한 JWT
     */
    private String generateJWT() throws IOException {
        // Private Key 읽기
        PrivateKey privateKey = readPrivateKey(privateKeyPath);
        
        // JWT 생성 (10분 유효)
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(600); // 10분
        
        return Jwts.builder()
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiration))
            .setIssuer(appId)
            .signWith(privateKey, SignatureAlgorithm.RS256)
            .compact();
    }
    
    /**
     * PEM 파일에서 Private Key 읽기
     */
    private PrivateKey readPrivateKey(String path) throws IOException {
        try (PEMParser pemParser = new PEMParser(new FileReader(path))) {
            Object object = pemParser.readObject();
            
            if (object instanceof PrivateKeyInfo) {
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                return converter.getPrivateKey((PrivateKeyInfo) object);
            } else if (object instanceof org.bouncycastle.openssl.PEMKeyPair) {
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                return converter.getPrivateKey(
                    ((org.bouncycastle.openssl.PEMKeyPair) object).getPrivateKeyInfo()
                );
            } else {
                throw new IOException("Unexpected PEM object type: " + 
                    (object != null ? object.getClass().getName() : "null"));
            }
        }
    }
    
    /**
     * 특정 Repository의 캐시된 토큰 제거
     * 토큰에 문제가 있을 때 수동으로 갱신하기 위해 사용
     */
    public void clearCache(String repoFullName) {
        tokenCacheMap.remove(repoFullName);
        log.info("Cleared cached token for {}", repoFullName);
    }
    
    /**
     * 모든 캐시된 토큰 제거
     */
    public void clearAllCache() {
        tokenCacheMap.clear();
        log.info("Cleared all cached tokens");
    }
}
