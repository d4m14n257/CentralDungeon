package com.centraldungeon.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * {@code /admin/settings} over HTTP (#141), and the row of the matrix it belongs to.
 *
 * <p><b>Row "Editar {@code system_settings}: sí / sí".</b> fase-3-admin-owner.md §3 puts editing
 * settings among the capabilities the two ranks share, and §7 names the way that gets broken:
 * {@code hasRole('ADMIN')} written where {@code hasAnyRole('ADMIN','OWNER')} was meant leaves the
 * owner outside a screen and nobody notices, because the test actor in development is usually an
 * admin. So the three routes are walked by <em>both</em> ranks with the same expectation - the same
 * assertion {@code AdminUserApiIT} makes for its seven.
 *
 * <p>It has to go over HTTP because four things exist nowhere else: the {@code @PreAuthorize} on
 * each method, {@code SettingKey}'s {@code @JsonValue} in both directions, the {@code ProblemDetail}
 * the frontend branches on (#197), and {@code SettingKey.from} turning an unknown path segment into
 * a 404 rather than a 500.
 *
 * <p><b>And the acceptance criterion of the slice, end to end</b>: «un admin cambia el tope por
 * archivo; la subida siguiente lo respeta sin reiniciar nada». That is one assertion about the cache
 * and one about {@code /settings/limits}, and neither is reachable from a service test.
 */
@SpringBootTest
@Testcontainers
class AdminSettingsApiIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static final String BASE = "/api/v1/admin/settings";

    private static final String FILE_CAP = "files.max_file_size_mb";

    /** Built by hand for the same reason {@code AdminUserApiIT} does: Boot 4 moved the annotation out. */
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private SystemSettingRepository settingRepository;

    @Autowired
    private SystemSettingChangeRepository changeRepository;

    @Autowired
    private SettingsService settingsService;

    private User owner;
    private User admin;
    private User plainPlayer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        changeRepository.deleteAll();
        settingRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        owner = person("the-owner");
        grant(owner, PlatformRole.OWNER);
        admin = person("the-admin");
        grant(admin, PlatformRole.ADMIN);
        plainPlayer = person("just-a-player");
        grant(plainPlayer, PlatformRole.PLAYER);

        userService.evictAuthCache(owner.getId());
        userService.evictAuthCache(admin.getId());
        userService.evictAuthCache(plainPlayer.getId());
    }

    // ---------------------------------------------------------------- the matrix

    /**
     * The row of the matrix, walked: an admin and an owner get the same answer on all three routes.
     * This is the assertion that would have caught a {@code hasRole('ADMIN')}.
     */
    @Test
    @DisplayName("every route answers the same to an admin and to an owner")
    void everyRouteAnswersTheSameToAnAdminAndToAnOwner() throws Exception {
        for (User actor : new User[] {admin, owner}) {
            as(get(BASE), actor).andExpect(status().isOk());
            as(get(BASE + "/" + FILE_CAP + "/history"), actor).andExpect(status().isOk());
            update(actor, FILE_CAP, 3, "la misma superficie para los dos").andExpect(status().isOk());
        }
    }

    /** Neither rank: the {@code @PreAuthorize} answers, with the code the frontend branches on. */
    @Test
    void aPlayerIsRefusedEveryAdminRoute() throws Exception {
        as(get(BASE), plainPlayer)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        as(get(BASE + "/" + FILE_CAP + "/history"), plainPlayer).andExpect(status().isForbidden());
        update(plainPlayer, FILE_CAP, 3, "nope").andExpect(status().isForbidden());
    }

    /** No token at all is a 401 and not a 403 - the difference is what the frontend renders. */
    @Test
    void anAnonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/settings/limits")).andExpect(status().isUnauthorized());
    }

    /**
     * <b>{@code /settings/limits} is for everybody with a session</b>, which is the whole reason it
     * is not under {@code /admin}: the caller is whoever is about to upload a file.
     */
    @Test
    void theClientLimitsAreReadableByAnybodySignedIn() throws Exception {
        as(get("/api/v1/settings/limits"), plainPlayer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxFileSizeBytes").value(2 * 1024 * 1024))
                .andExpect(jsonPath("$.claimTimeoutMinutes").value(15));
    }

    // ---------------------------------------------------------------- the listing

    /**
     * On an untouched database every key is there, showing its default and saying nobody has set it.
     * This is the "no row means the shipped default" contract, against real MySQL.
     */
    @Test
    @DisplayName("an untouched platform lists every key on its shipped default")
    void theListingCarriesEveryKeyEvenWithAnEmptyTable() throws Exception {
        assertThat(settingRepository.count()).isZero();

        as(get(BASE), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SettingKey.values().length))
                .andExpect(jsonPath("$[?(@.key=='" + FILE_CAP + "')].value").value(2))
                .andExpect(jsonPath("$[?(@.key=='" + FILE_CAP + "')].defaultValue").value(2))
                .andExpect(jsonPath("$[?(@.key=='" + FILE_CAP + "')].overridden").value(false))
                .andExpect(jsonPath("$[?(@.key=='profiles.visibility_window_days')].retroactive").value(true));
    }

    // ---------------------------------------------------------------- what the slice promises

    /**
     * <b>«Un admin cambia el tope por archivo; la subida siguiente lo respeta sin reiniciar nada».</b>
     *
     * <p>Three assertions, and the middle one is the point: the accessor is cached, so a change that
     * did not clear the cache would go on answering the old number for the rest of the TTL - and the
     * upload endpoint reads it through exactly this accessor. The third checks the same number
     * reaching the client, which is what stops the interface from refusing a file the server would
     * take.
     */
    @Test
    @DisplayName("a changed file cap applies to the next request, with no restart")
    void changingTheFileCapAppliesImmediately() throws Exception {
        assertThat(settingsService.maxFileSizeBytes()).isEqualTo(2L * 1024 * 1024);

        update(admin, FILE_CAP, 5, "los mapas pesan más de lo que pensábamos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(5))
                .andExpect(jsonPath("$.overridden").value(true))
                .andExpect(jsonPath("$.updatedByName").value("the-admin"));

        assertThat(settingsService.maxFileSizeBytes()).isEqualTo(5L * 1024 * 1024);
        as(get("/api/v1/settings/limits"), plainPlayer)
                .andExpect(jsonPath("$.maxFileSizeBytes").value(5 * 1024 * 1024));
    }

    /**
     * <b>«El cambio queda registrado con quién y cuándo».</b> A platform-wide change produces no
     * notification and no visible event, so the audit row is the only trace it leaves - and reading
     * it back is what keeps the table from being write-only (fase-3-admin-owner.md §7).
     */
    @Test
    @DisplayName("every change leaves its row, with who, when and why")
    void everyChangeIsRecordedAndReadableBack() throws Exception {
        update(admin, FILE_CAP, 5, "primero subimos").andExpect(status().isOk());
        update(owner, FILE_CAP, 3, "y después lo bajamos").andExpect(status().isOk());

        as(get(BASE + "/" + FILE_CAP + "/history"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // The first one records that there was no override before, which is a different fact
                // from "it was already 2".
                .andExpect(jsonPath("$[0].fromValue").doesNotExist())
                .andExpect(jsonPath("$[0].toValue").value("5"))
                .andExpect(jsonPath("$[0].changedByName").value("the-admin"))
                .andExpect(jsonPath("$[0].justification").value("primero subimos"))
                .andExpect(jsonPath("$[1].fromValue").value("5"))
                .andExpect(jsonPath("$[1].toValue").value("3"))
                .andExpect(jsonPath("$[1].changedByName").value("the-owner"));
    }

    /** A setting nobody ever touched has an empty history - not a 404, which is what a bad key gets. */
    @Test
    void aSettingNobodyChangedHasAnEmptyHistory() throws Exception {
        as(get(BASE + "/" + FILE_CAP + "/history"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------------------------------------------------------- what it refuses

    /** Out of range is a 400 carrying both bounds: "too big" without the numbers says nothing (#197). */
    @Test
    void aValueOutsideTheKeysRangeIsRefusedWithItsBounds() throws Exception {
        update(owner, FILE_CAP, 500, "enorme")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SETTING_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.errorParams.minValue").value("1"))
                .andExpect(jsonPath("$.errorParams.maxValue").value("25"));

        assertThat(settingRepository.count()).as("nothing is written when the value is refused").isZero();
        assertThat(changeRepository.count()).isZero();
    }

    /** A key that does not exist is a 404, not a 500 - {@code SettingKey.from} is what makes it one. */
    @Test
    void anUnknownSettingIsNotFound() throws Exception {
        update(owner, "files.max_file_size", 3, "casi")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        as(get(BASE + "/files.max_file_size/history"), owner).andExpect(status().isNotFound());
    }

    /** A blank reason is a 400: a change nobody explained is a change nobody can review (#141). */
    @Test
    void aBlankJustificationIsRefused() throws Exception {
        update(owner, FILE_CAP, 3, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /** A missing value is answered as a missing value, not silently taken as zero. */
    @Test
    void aMissingValueIsRefused() throws Exception {
        mockMvc.perform(bearer(put(BASE + "/" + FILE_CAP), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"sin número\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ---------------------------------------------------------------- fixtures

    private ResultActions update(User actor, String key, int value, String justification) throws Exception {
        return mockMvc.perform(bearer(put(BASE + "/" + key), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":" + value + ",\"justification\":\"" + justification + "\"}"));
    }

    private ResultActions as(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mockMvc.perform(bearer(request, actor));
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, User actor) {
        return request.header("Authorization", "Bearer " + jwtService.issueAccessToken(actor.getId()));
    }

    private User person(String discordUsername) {
        User user = new User(UUID.randomUUID().toString().replace("-", ""), discordUsername);
        user.setName(discordUsername);
        return userRepository.save(user);
    }

    private void grant(User user, PlatformRole role) {
        Role entity = roleRepository.findByName(role.roleName()).orElseThrow();
        userRoleRepository.save(new UserRole(user, entity));
    }
}
