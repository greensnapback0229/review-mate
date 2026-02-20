package greensnaback0229.pr_review_server.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class EncryptionService {

    private final TextEncryptor encryptor;

    public EncryptionService(@Value("${encryption.secret-key}") String secretKey) {
        String salt = generateStableSalt(secretKey);
        this.encryptor = Encryptors.text(secretKey, salt);
    }

    private String generateStableSalt(String secretKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secretKey.getBytes(StandardCharsets.UTF_8));
            byte[] saltBytes = new byte[8];
            System.arraycopy(hash, 0, saltBytes, 0, 8);
            return new String(Hex.encode(saltBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public String encrypt(String plainText) {
        return encryptor.encrypt(plainText);
    }

    public String decrypt(String cipherText) {
        return encryptor.decrypt(cipherText);
    }
}
