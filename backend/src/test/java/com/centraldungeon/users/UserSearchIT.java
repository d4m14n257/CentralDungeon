package com.centraldungeon.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.users.dto.AdminUserSummaryResponse;
import com.centraldungeon.users.dto.UserSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
    private AdminUserService adminUserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
        User juanma = save("juanma", "Juan Manuel", UserStatus.Allowed);
        save("pablosan", "Pablo Ruiz", UserStatus.Allowed);
        save("elpablo", "Juan Ignacio", UserStatus.Allowed);
        save("bannedjuan", "Juan Blocked", UserStatus.Blocked);
        grant(juanma, PlatformRole.MASTER);
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
        assertThat(discordNamesOf(userService.search("/discord_name juan /or /discord_name pablo", FIRST_PAGE)))
                .containsExactlyInAnyOrder("juanma", "pablosan", "elpablo");
        assertThat(discordNamesOf(userService.search("/discord_name pablo /and /user_name juan", FIRST_PAGE)))
                .containsExactly("elpablo");
    }

    /** The alternatives of one criterion: any one of them is enough (decisiones.md #164). */
    @Test
    void commasSeparateAlternativesOfTheSameCriterion() {
        assertThat(discordNamesOf(userService.search("/discord_name juanma,pablosan", FIRST_PAGE)))
                .containsExactlyInAnyOrder("juanma", "pablosan");
    }

    @Test
    void matchesRegardlessOfCase() {
        assertThat(discordNamesOf(userService.search("JUANMA", FIRST_PAGE))).containsExactly("juanma");
    }

    /**
     * The rule F3.1 could most easily have broken. {@code UserSearchSpecification} now serves two
     * audiences, and the picker's one still forces {@code status = Allowed} - no flag, no query and
     * no {@code /status} command can turn it off, because nobody blocked should ever be offered as a
     * master or a player.
     */
    @Test
    void neverOffersSomeoneWhoIsNotAllowed() {
        assertThat(discordNamesOf(userService.search("banned", FIRST_PAGE))).isEmpty();
        assertThat(discordNamesOf(userService.search("/user_name Blocked", FIRST_PAGE))).isEmpty();
        // /status is not part of the picker's vocabulary, so this is literal text - and finds nobody.
        assertThat(discordNamesOf(userService.search("/status Blocked", FIRST_PAGE))).isEmpty();
        assertThat(discordNamesOf(userService.search("  ", FIRST_PAGE))).doesNotContain("bannedjuan");
    }

    /** /admin/users is the other audience: an admin who cannot find the account they blocked cannot unblock it. */
    @Test
    void theAdminListingDoesSeeBlockedAccounts() {
        assertThat(adminDiscordNamesOf("banned")).containsExactly("bannedjuan");
        assertThat(adminDiscordNamesOf("/status Blocked")).containsExactly("bannedjuan");
        assertThat(adminDiscordNamesOf(null))
                .containsExactlyInAnyOrder("juanma", "pablosan", "elpablo", "bannedjuan");
    }

    @Test
    void theAdminListingFiltersByLiveRole() {
        assertThat(adminDiscordNamesOf("/role Master")).containsExactly("juanma");
        assertThat(adminDiscordNamesOf("/role Owner")).isEmpty();
    }

    /** Un valor desconocido no es un 400: no matchea nada (§2.5). */
    @Test
    void anUnknownRoleOrStatusMatchesNothingInsteadOfFailing() {
        assertThat(adminDiscordNamesOf("/role Wizard")).isEmpty();
        assertThat(adminDiscordNamesOf("/status Exploded")).isEmpty();
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

    private List<String> adminDiscordNamesOf(@Nullable String query) {
        return adminUserService.search(query, FIRST_PAGE).content().stream()
                .map(AdminUserSummaryResponse::discordUsername)
                .toList();
    }

    private User save(String discordUsername, String name, UserStatus status) {
        User user = new User(UUID.randomUUID().toString().replace("-", ""), discordUsername);
        user.setName(name);
        user.setStatus(status);
        return userRepository.save(user);
    }

    private void grant(User user, PlatformRole role) {
        Role entity = roleRepository.findByName(role.roleName()).orElseThrow();
        userRoleRepository.save(new UserRole(user, entity));
    }
}
