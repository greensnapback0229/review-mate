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

/**
 * GitHub App 인증을 처리하는 클래스
 * 
 * 동작 방식:
 * 1. Private Key로 JWT 생성
 * 2. JWT로 Installation Access Token 발급
 * 3. Token은 1시간 유효 (자동 갱신)
 */
@Slf4j
@Component
public class GitHubAppAuthenticator {
    
    @Value("${github.app.id}")
    private String appId;
    
    @Value("${github.app.installation-id}")
    private String installationId;
    
    @Value("${github.app.private-key-path}")
    private String privateKeyPath;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    private String cachedToken;
    private Instant tokenExpiresAt;
    
    /**
     * Installation Access Token 발급
     * 캐시된 토큰이 유효하면 재사용, 만료되면 재발급
     */
    public String getInstallationToken() throws IOException {
        // 캐시된 토큰이 유효하면 재사용
        if (cachedToken != null && tokenExpiresAt != null 
            && Instant.now().isBefore(tokenExpiresAt.minusSeconds(300))) { // 5분 여유
            log.debug("Using cached installation token");
            return cachedToken;
        }
        
        log.info("Generating new installation token");
        
        // 1. JWT 생성
        String jwt = generateJWT();
        
        // 2. Installation Token 발급
        String url = String.format(
            "https://api.github.com/app/installations/%s/access_tokens",
            installationId
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("Authorization", "Bearer " + jwt);
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        
        if (response.getStatusCode() == HttpStatus.CREATED) {
            Map<String, Object> body = response.getBody();
            cachedToken = (String) body.get("token");
            String expiresAt = (String) body.get("expires_at");
            tokenExpiresAt = Instant.parse(expiresAt);
            
            log.info("Installation token generated successfully, expires at: {}", expiresAt);
            return cachedToken;
        } else {
            throw new RuntimeException("Failed to get installation token: " + response.getStatusCode());
        }
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
}
