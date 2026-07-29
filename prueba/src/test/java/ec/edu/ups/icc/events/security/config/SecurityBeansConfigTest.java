package ec.edu.ups.icc.events.security.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityBeansConfigTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityBeansConfig config = new SecurityBeansConfig();
        passwordEncoder = config.passwordEncoder();
    }

    @Test
    void shouldCreatePasswordEncoder() {
        assertNotNull(passwordEncoder);
    }

    @Test
    void shouldEncryptPasswordUsingBCrypt() {
        String rawPassword = "ClaveSegura123!";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertNotNull(encodedPassword);
        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(encodedPassword.startsWith("$2"));
        assertTrue(passwordEncoder.matches(
                rawPassword,
                encodedPassword
        ));
    }

    @Test
    void shouldRejectIncorrectPassword() {
        String encodedPassword =
                passwordEncoder.encode("ClaveCorrecta123!");

        assertFalse(passwordEncoder.matches(
                "ClaveIncorrecta123!",
                encodedPassword
        ));
    }

    @Test
    void shouldGenerateDifferentHashesForSamePassword() {
        String rawPassword = "ClaveSegura123!";

        String firstHash = passwordEncoder.encode(rawPassword);
        String secondHash = passwordEncoder.encode(rawPassword);

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordEncoder.matches(
                rawPassword,
                firstHash
        ));
        assertTrue(passwordEncoder.matches(
                rawPassword,
                secondHash
        ));
    }
}