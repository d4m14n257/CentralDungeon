package com.centraldungeon.catalogs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * What the catalog canvas (#275) calls over HTTP that {@code CatalogGroupIT} cannot reach: the
 * {@code @PreAuthorize} on the two operations of #276 and the {@code ?groupsOnly=} parameter.
 *
 * <p>Both ranks walk the same routes with the same expectation, for the reason
 * {@code AdminSettingsApiIT} gives: {@code hasRole('ADMIN')} written where
 * {@code hasAnyRole('ADMIN','OWNER')} was meant leaves the owner out and nobody notices (#169).
 */
@SpringBootTest
@Testcontainers
class AdminCatalogApiIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static final String BASE = "/api/v1/admin/catalogs/tags";

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
    private TagRepository tagRepository;

    private User owner;
    private User admin;
    private User plainPlayer;

    private String head;
    private String otherHead;
    private String alias;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        userRoleRepository.deleteAll();
        userRepository.deleteAll();
        owner = person("the-owner");
        grant(owner, PlatformRole.OWNER);
        admin = person("the-admin");
        grant(admin, PlatformRole.ADMIN);
        plainPlayer = person("just-a-player");
        grant(plainPlayer, PlatformRole.PLAYER);
        List.of(owner, admin, plainPlayer).forEach(user -> userService.evictAuthCache(user.getId()));

        clearTags();
        head = tag("One-shot", null);
        otherHead = tag("Campaign", null);
        alias = tag("Oneshot", head);
    }

    /** The matrix row, walked by both ranks: move an alias, then promote it where it landed. */
    @Test
    void anAdminAndAnOwnerCanReassignAndPromote() throws Exception {
        for (User actor : List.of(admin, owner)) {
            String from = canonicalIdOf(alias);
            String to = from.equals(head) ? otherHead : head;

            reassign(actor, alias, to).andExpect(status().isOk()).andExpect(jsonPath("$.canonicalId").value(to));
            as(post(BASE + "/" + alias + "/promote"), actor).andExpect(status().isOk())
                    .andExpect(jsonPath("$.canonicalId").doesNotExist());

            // Put the fixture back as it was, for the second rank.
            resetGroups();
        }
    }

    @Test
    void aPlayerIsRefusedTheNewRoutes() throws Exception {
        reassign(plainPlayer, alias, otherHead).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        as(post(BASE + "/" + alias + "/promote"), plainPlayer).andExpect(status().isForbidden());
        as(get(BASE).param("groupsOnly", "true"), plainPlayer).andExpect(status().isForbidden());
        assertThat(canonicalIdOf(alias)).isEqualTo(head);
    }

    /** Moving a head alone is a merge, and the 409 says so rather than building a second level (#59). */
    @Test
    void reassigningAHeadIsAConflict() throws Exception {
        reassign(admin, head, otherHead).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    /** #275: the table's listing has one row per group, and says how big each one is. */
    @Test
    void theGroupsOnlyListingLeavesAliasesOut() throws Exception {
        as(get(BASE).param("groupsOnly", "true"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.name == 'One-shot')].aliasCount").value(1));
    }

    private ResultActions reassign(User actor, String id, String canonicalId) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + id + "/reassign"), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"canonicalId\":\"" + canonicalId + "\"}"));
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

    /** Flattens the groups before deleting, because canonical_id is a real self-referencing FK. */
    private void clearTags() {
        List<Tag> all = tagRepository.findAll();
        all.forEach(tag -> tag.setCanonicalId(null));
        tagRepository.saveAll(all);
        tagRepository.deleteAll();
    }

    private void resetGroups() {
        List<Tag> all = tagRepository.findAll();
        all.forEach(tag -> tag.setCanonicalId(tag.getId().equals(alias) ? head : null));
        tagRepository.saveAll(all);
    }

    private String tag(String name, String canonicalId) {
        Tag tag = new Tag(name);
        tag.setStatus(CatalogStatus.Accepted);
        tag.setCanonicalId(canonicalId);
        return tagRepository.save(tag).getId();
    }

    private String canonicalIdOf(String id) {
        return tagRepository.findById(id).orElseThrow().getCanonicalId();
    }
}
