package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.catalogs.CatalogStatus;
import com.centraldungeon.catalogs.Tag;
import com.centraldungeon.catalogs.TableCatalogLinkStatus;
import com.centraldungeon.catalogs.TableTag;
import com.centraldungeon.catalogs.TableTagRepository;
import com.centraldungeon.catalogs.TagRepository;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The explorer's search, against real MySQL (#54, #56, #246).
 *
 * <p><b>None of this can be proven with a mocked repository.</b> The synonym group resolves across
 * two tables and the match is an {@code exists} subquery over a third; what a unit test sees is a
 * lambda it cannot execute. And the one failure mode that matters most - a table coming back twice
 * because it carries two members of the same group - only exists once there is SQL.
 *
 * <p>Wired with @DynamicPropertySource, not @ServiceConnection: see RegistrationServiceIT for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class GameTableSearchIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private GameTableService gameTableService;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterRepository masterRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TableTagRepository tableTagRepository;

    private User player;
    private User master;

    @BeforeEach
    void setUp() {
        tableTagRepository.deleteAll();
        masterRepository.deleteAll();
        gameTableRepository.deleteAll();
        // tags.canonical_id points at tags, so the aliases have to let go of their group before the
        // rows can be dropped - otherwise the cleanup dies on its own foreign key.
        List<Tag> tags = tagRepository.findAll();
        tags.forEach(each -> each.setCanonicalId(null));
        tagRepository.saveAll(tags);
        tagRepository.deleteAll();
        userRepository.deleteAll();

        player = userRepository.save(new User(randomDiscordId(), "Player"));
        master = userRepository.save(new User(randomDiscordId(), "Master"));

        Tag canonical = accepted("D&D", null);
        Tag alias = accepted("DANDD", canonical.getId());
        Tag unrelated = accepted("Horror", null);
        Tag proposed = proposed("Cyberpunk");

        tag(opened("Curse of Strahd"), alias);
        tag(opened("Waterdeep Dragon Heist"), canonical);
        tag(opened("Nightmares"), unrelated);
        tag(opened("Neon Streets"), proposed);
    }

    @Test
    @DisplayName("searching by the canonical entry finds the tables tagged with its alias (#56)")
    void findsTablesTaggedWithAnAliasWhenSearchingTheCanonicalEntry() {
        assertThat(namesOf("/table_tag D&D"))
                .containsExactlyInAnyOrder("Curse of Strahd", "Waterdeep Dragon Heist");
    }

    @Test
    @DisplayName("searching by an alias finds the tables tagged with the canonical entry (#54)")
    void findsTablesTaggedWithTheCanonicalEntryWhenSearchingAnAlias() {
        assertThat(namesOf("/table_tag DANDD"))
                .containsExactlyInAnyOrder("Curse of Strahd", "Waterdeep Dragon Heist");
    }

    /**
     * The bug the {@code exists} exists to prevent. A join over the bridge table would return this
     * table once per group member it carries, so the page would hold two rows covering one table -
     * and the total, which the frontend paginates on, would be wrong too.
     */
    @Test
    @DisplayName("a table tagged with two members of the same group comes back once, not twice")
    void doesNotDuplicateATableThatCarriesTwoMembersOfTheGroup() {
        GameTable both = opened("Tomb of Annihilation");
        tag(both, tagRepository.findByNameIgnoreCase("D&D").orElseThrow());
        tag(both, tagRepository.findByNameIgnoreCase("DANDD").orElseThrow());

        PageResponse<GameTableSummaryResponse> page = gameTableService.list("/table_tag D&D", FIRST_PAGE, player.getId());

        assertThat(page.content()).extracting(GameTableSummaryResponse::name).containsOnlyOnce("Tomb of Annihilation");
        assertThat(page.totalElements()).isEqualTo(3);
    }

    /**
     * The case that proves the equivalence is <b>symmetric and flat</b> (#54, #59): two aliases of the
     * same group find each other without either of them being the canonical entry. A resolution that
     * only walked alias to canonical would pass every other test here and fail this one.
     */
    @Test
    @DisplayName("one alias finds the tables labelled with a sibling alias, neither of them canonical")
    void findsTablesAcrossTwoAliasesOfTheSameGroup() {
        Tag canonical = tagRepository.findByNameIgnoreCase("D&D").orElseThrow();
        Tag sibling = accepted("DND", canonical.getId());
        tag(opened("Descent into Avernus"), sibling);

        // "DANDD" and "DND" are both aliases of "D&D": neither is the group's root.
        assertThat(namesOf("/table_tag DANDD"))
                .contains("Descent into Avernus")
                .contains("Curse of Strahd");
    }

    @Test
    @DisplayName("a value nobody accepted does not filter (#57)")
    void doesNotResolveAValueStillInCreated() {
        assertThat(namesOf("/table_tag Cyberpunk")).isEmpty();
    }

    /**
     * «No accepted value is called that» is not «no filter». Read the wrong way, one typo lists the
     * whole platform - which is the single most expensive way this could fail, because it looks like
     * it worked.
     */
    @Test
    @DisplayName("a word that names no value matches no table, not every table")
    void matchesNothingWhenTheWordNamesNoValue() {
        assertThat(namesOf("/table_tag pathfimder")).isEmpty();
    }

    @Test
    @DisplayName("a tag the master took off the table stops matching it (#190)")
    void ignoresARemovedLink() {
        TableTag link = tableTagRepository.findAll().stream()
                .filter(each -> each.getId().tagId().equals(tagRepository.findByNameIgnoreCase("Horror").orElseThrow().getId()))
                .findFirst()
                .orElseThrow();
        link.setStatus(TableCatalogLinkStatus.Removed);
        tableTagRepository.save(link);

        assertThat(namesOf("/table_tag Horror")).isEmpty();
    }

    @Test
    @DisplayName("a bare term searches the table's name")
    void searchesTheNameWithoutACommand() {
        assertThat(namesOf("waterdeep")).containsExactly("Waterdeep Dragon Heist");
    }

    @Test
    @DisplayName("an empty box lists everything visible")
    void listsEverythingVisibleWithAnEmptyBox() {
        assertThat(namesOf(null)).hasSize(4);
    }

    /**
     * The visibility rule and the search are joined with {@code and} and never folded together, so a
     * criterion cannot reach across it: the master searching for their own table still does not find
     * it (#154).
     */
    @Test
    @DisplayName("a search never reaches a table the actor runs")
    void neverReturnsATableTheActorRuns() {
        assertThat(nameList(gameTableService.list("/table_tag D&D", FIRST_PAGE, master.getId()))).isEmpty();
    }

    @Test
    @DisplayName("two criteria narrow, and /or widens")
    void combinesCriteriaWithTheConnectorsOfTheLanguage() {
        assertThat(namesOf("/table_tag D&D /and /table_name strahd")).containsExactly("Curse of Strahd");
        assertThat(namesOf("/table_tag Horror /or /table_name waterdeep"))
                .containsExactlyInAnyOrder("Nightmares", "Waterdeep Dragon Heist");
    }

    // ------------------------------------------------------------------ setup

    private List<String> namesOf(String query) {
        return nameList(gameTableService.list(query, FIRST_PAGE, player.getId()));
    }

    private static List<String> nameList(PageResponse<GameTableSummaryResponse> page) {
        return page.content().stream().map(GameTableSummaryResponse::name).toList();
    }

    private Tag accepted(String name, @org.jspecify.annotations.Nullable String canonicalId) {
        Tag tag = new Tag(name);
        tag.setStatus(CatalogStatus.Accepted);
        tag.setCanonicalId(canonicalId);
        return tagRepository.save(tag);
    }

    private Tag proposed(String name) {
        return tagRepository.save(new Tag(name));
    }

    private GameTable opened(String name) {
        GameTable table = new GameTable(name, master);
        table.setStatus(GameTableStatus.Opened);
        GameTable saved = gameTableRepository.save(table);
        masterRepository.save(new Master(saved, master, MasterType.Primary));
        return saved;
    }

    private void tag(GameTable table, Tag tag) {
        tableTagRepository.save(new TableTag(table.getId(), tag.getId()));
    }

    /** discord_id is VARCHAR(32); a UUID with the dashes stripped fits exactly. */
    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
