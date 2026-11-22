package com.centraldungeon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The daily purge against real MySQL: which {@code refresh_tokens} rows survive.
 *
 * <p>The delete is one JPQL statement with an {@code or}, so what is worth proving is the rows on each
 * side of every boundary - live, recently expired, long expired, and revoked either side of its expiry -
 * rather than the arithmetic of the cutoff.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class RefreshTokenPurgeIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private RefreshTokenPurgeService purgeService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void purgesTheRowsThatCanNoLongerMatterAndKeepsTheRest() {
        User user = userRepository.save(new User(randomId(), "Purge"));
        LocalDateTime now = LocalDateTime.now();

        String live = issue(user, now.plusDays(1), false);
        String recentlyExpired = issue(user, now.minusDays(1), false);
        String longExpired = issue(user, now.minusDays(8), false);
        String revokedStillLive = issue(user, now.plusDays(1), true);
        String revokedAndExpired = issue(user, now.minusHours(1), true);

        int deleted = purgeService.purgeStaleTokens();

        assertThat(deleted).isEqualTo(2);
        assertThat(refreshTokenRepository.findByJtiHash(live)).isPresent();
        assertThat(refreshTokenRepository.findByJtiHash(recentlyExpired)).isPresent();
        assertThat(refreshTokenRepository.findByJtiHash(revokedStillLive)).isPresent();
        assertThat(refreshTokenRepository.findByJtiHash(longExpired)).isEmpty();
        assertThat(refreshTokenRepository.findByJtiHash(revokedAndExpired)).isEmpty();
    }

    private String issue(User user, LocalDateTime expiresAt, boolean revoked) {
        String jtiHash = randomId() + randomId();
        RefreshToken token = new RefreshToken(jtiHash, randomId(), user.getId(), expiresAt);
        if (revoked) {
            token.revoke(LocalDateTime.now());
        }
        refreshTokenRepository.save(token);
        return jtiHash;
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
