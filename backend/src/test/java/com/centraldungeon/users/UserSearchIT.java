package com.centraldungeon.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.users.dto.UserSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The search predicate is built at runtime out of the parsed query (UserSearchSpecification), so
 * what has to be proven is the SQL it produces - the connectors, the case-insensitive match and the
 * status filter. A mocked repository would assert nothing about any of that.
 *
 * Wired with @DynamicPropertySource, not @ServiceConnection: see RegistrationServiceIT for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class UserSearchIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20, Sort.by("discordUsername"));

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        save("juanma", "Juan Manuel", UserStatus.Allowed);
        save("pablosan", "Pablo Ruiz", UserStatus.Allowed);
        save("elpablo", "Juan Ignacio", UserStatus.Allowed);
        save("bannedjuan", "Juan Blocked", UserStatus.Blocked);
    }

    @Test
    void plainTextMatchesEitherName() {
        assertThat(discordNamesOf(userService.search("juan", FIRST_PAGE)))
                .containsExactlyInAnyOrder("juanma", "elpablo");
    }

    @Test
    void aFieldPrefixNarrowsToThatField() {
        assertThat(discordNamesOf(userService.search("/discord_name juan", FIRST_PAGE))).containsExactly("juanma");
        assertThat(discordNamesOf(userService.search("/user_name juan", FIRST_PAGE)))
                .containsExactlyInAnyOrder("juanma", "elpablo");
    }

    @Test
    void orWidensAndAndNarrows() {
        assertThat(discordNamesOf(userService.search("/discord_name juan or /discord_name pablo", FIRST_PAGE)))
                .containsExactlyInAnyOrder("juanma", "pablosan", "elpablo");
        assertThat(discordNamesOf(userService.search("/discord_name pablo and /user_name juan", FIRST_PAGE)))
                .containsExactly("elpablo");
    }

    @Test
    void matchesRegardlessOfCase() {
        assertThat(discordNamesOf(userService.search("JUANMA", FIRST_PAGE))).containsExactly("juanma");
    }

    @Test
    void neverOffersSomeoneWhoIsNotAllowed() {
        assertThat(discordNamesOf(userService.search("banned", FIRST_PAGE))).isEmpty();
    }

    @Test
    void wildcardsTypedByHandAreSearchedAsText() {
        assertThat(discordNamesOf(userService.search("%", FIRST_PAGE))).isEmpty();
    }

    @Test
    void anEmptyQueryListsAllowedUsers() {
        assertThat(discordNamesOf(userService.search("  ", FIRST_PAGE)))
                .containsExactlyInAnyOrder("juanma", "pablosan", "elpablo");
    }

    private List<String> discordNamesOf(PageResponse<UserSummaryResponse> page) {
        return page.content().stream().map(UserSummaryResponse::discordUsername).toList();
    }

    private void save(String discordUsername, String name, UserStatus status) {
        User user = new User(UUID.randomUUID().toString().replace("-", ""), discordUsername);
        user.setName(name);
        user.setStatus(status);
        userRepository.save(user);
    }
}
