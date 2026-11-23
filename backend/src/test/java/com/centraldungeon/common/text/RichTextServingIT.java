package com.centraldungeon.common.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.features.registrations.TableRegistration;
import com.centraldungeon.features.registrations.TableRegistrationRepository;
import com.centraldungeon.features.registrations.TableRegistrationStatus;
import com.centraldungeon.features.tables.GameTable;
import com.centraldungeon.features.tables.GameTableRepository;
import com.centraldungeon.features.tables.GameTableStatus;
import com.centraldungeon.features.tables.Master;
import com.centraldungeon.features.tables.MasterRepository;
import com.centraldungeon.features.tables.MasterType;
import com.centraldungeon.features.tasks.TableTask;
import com.centraldungeon.features.tasks.TableTaskRepository;
import com.centraldungeon.features.tasks.TaskAudience;
import com.centraldungeon.features.tasks.TaskSubmission;
import com.centraldungeon.features.tasks.TaskSubmissionRepository;
import com.centraldungeon.features.users.PlatformRole;
import com.centraldungeon.features.users.User;
import com.centraldungeon.features.users.UserRepository;
import com.centraldungeon.features.users.UserRole;
import com.centraldungeon.features.users.UserRoleRepository;
import com.centraldungeon.features.users.UserService;
import com.centraldungeon.features.users.RoleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The sanitizer on the way <b>out</b>, proven with rows that never went through the way in.
 *
 * <p>The rich text of a table, a request and an answer is sanitized when it is saved and again when
 * it is served, and the second pass only matters for text the first one never saw: a row written
 * before the gate existed, restored from a backup, or inserted by any path that skips the services.
 * So the hostile text here is written with {@code JdbcTemplate}, straight into the columns, and
 * then asked for through <b>every</b> endpoint that returns one of those fields. A mapper that
 * reads the entity and forgets the sanitizer would pass every other test in the project, because
 * everything else stores through the service.
 *
 * <p>"Safe" is checked structurally and not by looking for known bad strings: every tag left in the
 * response has to be on the allowlist, carry no attribute but an http(s) {@code href} and the
 * {@code rel}, and nothing outside a tag may contain a raw {@code <}. That also catches the payload
 * nobody thought of.
 */
@SpringBootTest
@Testcontainers
class RichTextServingIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    /** What a sane row carries before the hostile part, so a response can be told from an empty one. */
    private static final String KEPT = "<p>kept</p>";

    /** The names under which a response carries user-written rich text. */
    private static final Set<String> RICH_FIELDS = Set.of("description", "permitted", "requirements", "content");

    /**
     * A tag the allowlist can leave: its name, and at most the nofollow rel and an href that is either
     * http(s) or has no scheme at all (relative and protocol-relative links resolve to the page's own
     * http(s), so they cannot carry script).
     */
    private static final Pattern SAFE_TAG = Pattern.compile(
            "<(?:/?(?:p|br|strong|em|u|s|code|pre|blockquote|h1|h2|h3|ul|ol|li)\\s*/?"
                    + "|/a|a(?:\\s+href=\"(?:https?:[^\"<>]*|[^\":<>]*)\")?(?:\\s+rel=\"nofollow\")?)>");

    /** Anything that looks like the start or end of a tag. */
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");

    private static final ObjectMapper JSON = new ObjectMapper();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterRepository masterRepository;

    @Autowired
    private TableRegistrationRepository registrationRepository;

    @Autowired
    private TableTaskRepository taskRepository;

    @Autowired
    private TaskSubmissionRepository submissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;
    private User primary;
    private User player;
    private GameTable table;
    private TableTask playersTask;
    private TableTask candidatesTask;
    private TaskSubmission submission;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        admin = person("serving-admin", PlatformRole.ADMIN);
        primary = person("serving-primary", PlatformRole.PLAYER, PlatformRole.MASTER);
        player = person("serving-player", PlatformRole.PLAYER);

        GameTable created = new GameTable("Serving " + UUID.randomUUID(), primary);
        created.setStatus(GameTableStatus.InProgress);
        created.setMaxPlayers(5);
        table = gameTableRepository.save(created);
        masterRepository.save(new Master(table, primary, MasterType.Primary));
        TableRegistration registration = new TableRegistration(table, player, "I would like to play");
        registration.setStatus(TableRegistrationStatus.Player);
        registrationRepository.save(registration);

        playersTask = taskRepository.save(new TableTask(table, TaskAudience.Players, "For players"));
        candidatesTask = taskRepository.save(new TableTask(table, TaskAudience.Candidates, "For candidates"));
        submission = submissionRepository.save(new TaskSubmission(playersTask, player, KEPT));
    }

    /** The payloads: each is a way of getting script, a request or a style to a browser through rich text. */
    static Stream<Arguments> hostilePayloads() {
        return Stream.of(
                Arguments.of("script tag", "<script>alert(1)</script>"),
                Arguments.of("script tag in mixed case", "<ScRiPt>alert(1)</sCrIpT>"),
                Arguments.of("script tag with a closing trick", "<scr<script>ipt>alert(1)</scr</script>ipt>"),
                Arguments.of("img onerror", "<img src=x onerror=alert(1)>"),
                Arguments.of("img to a tracker", "<img src=\"https://tracker.example/x.png\">"),
                Arguments.of("onclick on an allowed tag", "<p onclick=\"steal()\">click</p>"),
                Arguments.of("onmouseover on a link", "<a href=\"https://ok.example\" onmouseover=\"steal()\">hover</a>"),
                Arguments.of("javascript href", "<a href=\"javascript:alert(1)\">x</a>"),
                Arguments.of("javascript href in mixed case", "<a href=\"JaVaScRiPt:alert(1)\">x</a>"),
                Arguments.of("javascript href with a tab inside", "<a href=\"java\tscript:alert(1)\">x</a>"),
                Arguments.of("javascript href with a leading space", "<a href=\" javascript:alert(1)\">x</a>"),
                Arguments.of("javascript href as a numeric entity", "<a href=\"&#106;avascript:alert(1)\">x</a>"),
                Arguments.of("javascript href as a hex entity", "<a href=\"&#x6A;avascript:alert(1)\">x</a>"),
                Arguments.of("data href", "<a href=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">x</a>"),
                Arguments.of("mailto href", "<a href=\"mailto:someone@example.org\">write</a>"),
                Arguments.of("vbscript href", "<a href=\"vbscript:msgbox(1)\">x</a>"),
                Arguments.of("protocol-relative href", "<a href=\"//evil.example/x\">x</a>"),
                Arguments.of("target blank on a link", "<a href=\"https://ok.example\" target=\"_blank\">x</a>"),
                Arguments.of("style attribute", "<p style=\"background:url(javascript:alert(1))\">s</p>"),
                Arguments.of("style tag", "<style>body{display:none}</style>"),
                Arguments.of("class attribute", "<p class=\"fixed inset-0\">c</p>"),
                Arguments.of("iframe", "<iframe src=\"https://evil.example\"></iframe>"),
                Arguments.of("iframe srcdoc", "<iframe srcdoc=\"<script>alert(1)</script>\"></iframe>"),
                Arguments.of("svg with script", "<svg><script>alert(1)</script></svg>"),
                Arguments.of("svg onload", "<svg onload=alert(1)>"),
                Arguments.of("math and style mutation", "<math><mtext><style><img src=x onerror=alert(1)></style></mtext></math>"),
                Arguments.of("noscript attribute breakout", "<noscript><p title=\"</noscript><img src=x onerror=alert(1)>\">"),
                Arguments.of("object", "<object data=\"https://evil.example/x.swf\"></object>"),
                Arguments.of("embed", "<embed src=\"https://evil.example/x.swf\">"),
                Arguments.of("form with input", "<form action=\"https://evil.example\"><input name=\"p\"><button>go</button></form>"),
                Arguments.of("base tag", "<base href=\"https://evil.example/\">"),
                Arguments.of("meta refresh", "<meta http-equiv=\"refresh\" content=\"0;url=https://evil.example\">"),
                Arguments.of("link tag", "<link rel=\"stylesheet\" href=\"https://evil.example/x.css\">"),
                Arguments.of("html comment with a tag", "<!--><script>alert(1)</script>-->"),
                Arguments.of("unclosed tag", "<a href=\"https://ok.example\" <script>alert(1)</script>"),
                Arguments.of("entity-encoded script as text", "&lt;script&gt;alert(1)&lt;/script&gt;"),
                Arguments.of("double-encoded script as text", "&amp;lt;script&amp;gt;alert(1)&amp;lt;/script&amp;gt;"),
                Arguments.of("null byte inside a tag name", "<scr\u0000ipt>alert(1)</scr\u0000ipt>"));
    }

    /** Without this the checks below could pass over empty responses, so the benign half has to arrive. */
    @Test
    void aBenignRowIsServedIntactThroughEveryEndpoint() throws Exception {
        writeRichText(KEPT);

        assertEveryEndpointServes(KEPT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hostilePayloads")
    void noEndpointServesHostileTextThatWasWrittenStraightIntoTheDatabase(String label, String hostile) throws Exception {
        writeRichText(KEPT + hostile);

        assertEveryEndpointServes(KEPT + hostile);
    }

    /** Writes the same text into every rich-text column, bypassing the services entirely. */
    private void writeRichText(String html) {
        jdbcTemplate.update(
                "UPDATE game_tables SET description = ?, permitted = ?, requirements = ? WHERE id = ?",
                html, html, html, table.getId());
        jdbcTemplate.update("UPDATE table_tasks SET description = ? WHERE game_table_id = ?", html, table.getId());
        jdbcTemplate.update("UPDATE task_submissions SET content = ? WHERE id = ?", html, submission.getId());
    }

    /**
     * Asks every endpoint that returns rich text and checks what came back.
     *
     * <p>Each call states how many rich fields it expects to carry text, so an endpoint that quietly
     * stopped returning the field cannot pass. The mutating ones go last, because closing a task
     * takes it off the applicable list.
     */
    private void assertEveryEndpointServes(String written) throws Exception {
        String tableId = table.getId();
        String tasks = "/api/v1/game-tables/" + tableId + "/tasks";

        // The table: three fields each, for the reader, for the people running it, and for an admin.
        assertSafe(as(get("/api/v1/game-tables/" + tableId), player), 3, written);
        assertSafe(as(get("/api/v1/game-tables/" + tableId), primary), 3, written);
        assertSafe(as(get("/api/v1/game-tables/" + tableId), admin), 3, written);
        assertSafe(as(get("/api/v1/game-tables/" + tableId + "/managed"), primary), 3, written);
        assertSafe(as(get("/api/v1/game-tables/" + tableId + "/managed"), admin), 3, written);

        // The tasks: the board of the masters and the admin, and the list of whoever is asked.
        assertSafe(as(get(tasks), primary), 2, written);
        assertSafe(as(get(tasks), admin), 2, written);
        assertSafe(as(get(tasks + "/applicable"), player), 2, written);

        // The answers: what the masters and the admin read, and what the author reads of their own.
        assertSafe(as(get("/api/v1/tasks/" + playersTask.getId() + "/submissions"), primary), 1, written);
        assertSafe(as(get("/api/v1/tasks/" + playersTask.getId() + "/submissions"), admin), 1, written);
        assertSafe(as(get("/api/v1/tasks/" + playersTask.getId() + "/submissions/mine"), player), 1, written);

        // The writes that answer with the thing they touched.
        assertSafe(
                as(put("/api/v1/game-tables/" + tableId + "/seeking-players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seeking\":true}"), primary),
                3,
                written);
        assertSafe(as(post("/api/v1/tasks/" + candidatesTask.getId() + "/close"), primary), 1, written);
    }

    /** Checks the status, collects every rich field of the response, and tests each one. */
    private void assertSafe(String body, int expectedFields, String written) throws Exception {
        List<String> fields = new ArrayList<>();
        collectRichFields(JSON.readTree(body), fields);

        assertThat(fields).as("rich fields served for %s", written).hasSize(expectedFields);
        for (String served : fields) {
            assertThat(served).as("served text").contains(KEPT);
            assertSafeHtml(served);
        }
    }

    private static void collectRichFields(JsonNode node, List<String> into) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (RICH_FIELDS.contains(entry.getKey()) && entry.getValue().isString()) {
                    into.add(entry.getValue().asString());
                } else {
                    collectRichFields(entry.getValue(), into);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectRichFields(child, into));
        }
    }

    /** Every tag is on the allowlist with allowed attributes only, and no raw {@code <} remains outside one. */
    private static void assertSafeHtml(String html) {
        var tags = ANY_TAG.matcher(html);
        while (tags.find()) {
            assertThat(tags.group()).as("a tag outside the allowlist in: %s", html).matches(SAFE_TAG);
        }
        assertThat(ANY_TAG.matcher(html).replaceAll("")).as("markup outside a tag in: %s", html).doesNotContain("<");
    }

    /** Performs the request as the given user and returns the body, after asserting it was a success. */
    private String as(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.issueAccessToken(actor.getId())))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private User person(String label, PlatformRole... roles) {
        User user = new User(UUID.randomUUID().toString().replace("-", ""), label + "-" + UUID.randomUUID().toString().substring(0, 6));
        user.setName(label);
        user.setCountry("AR");
        User saved = userRepository.save(user);
        for (PlatformRole role : roles) {
            userRoleRepository.save(new UserRole(saved, roleRepository.findByName(role.roleName()).orElseThrow()));
        }
        userService.evictAuthCache(saved.getId());
        return saved;
    }
}
