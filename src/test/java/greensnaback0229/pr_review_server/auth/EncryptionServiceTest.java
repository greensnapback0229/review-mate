package greensnaback0229.pr_review_server.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EncryptionService 테스트")
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService("test-encryption-key-32bytes-long!!");
    }

    @Test
    @DisplayName("encrypt_복호화시원문일치")
    void encrypt_복호화시원문일치() {
        // given
        String plainText = "sk-ant-api03-test-key-12345";

        // when
        String encrypted = encryptionService.encrypt(plainText);
        String decrypted = encryptionService.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(plainText);
        assertThat(encrypted).isNotEqualTo(plainText);
    }

    @Test
    @DisplayName("encrypt_동일값다른암호문")
    void encrypt_동일값다른암호문() {
        // given
        String plainText = "sk-ant-api03-test-key-12345";

        // when
        String encrypted1 = encryptionService.encrypt(plainText);
        String encrypted2 = encryptionService.encrypt(plainText);

        // then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("decrypt_잘못된암호문_예외발생")
    void decrypt_잘못된암호문_예외발생() {
        // given
        String invalidCipherText = "this-is-not-valid-cipher-text";

        // when & then
        assertThatThrownBy(() -> encryptionService.decrypt(invalidCipherText))
                .isInstanceOf(Exception.class);
    }
}
