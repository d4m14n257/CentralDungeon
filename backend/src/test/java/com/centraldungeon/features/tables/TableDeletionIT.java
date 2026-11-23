package com.centraldungeon.features.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.centraldungeon.features.catalogs.CatalogStatus;
import com.centraldungeon.features.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.features.catalogs.CatalogType;
import com.centraldungeon.features.catalogs.TableCatalogLinkStatus;
import com.centraldungeon.features.catalogs.TableCatalogService;
import com.centraldungeon.features.catalogs.TableTag;
import com.centraldungeon.features.catalogs.TableTagRepository;
import com.centraldungeon.features.catalogs.Tag;
import com.centraldungeon.features.catalogs.TagRepository;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.features.users.PlatformRole;
import com.centraldungeon.features.users.Role;
import com.centraldungeon.features.users.RoleRepository;
import com.centraldungeon.features.users.User;
import com.centraldungeon.features.users.UserRepository;
import com.centraldungeon.features.users.UserRole;
import com.centraldungeon.features.users.UserRoleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * A deleted table is gone for every read, and what it carried goes with it.
 *
 * <p>Deleting marks the table and nothing physically disappears, so every reader has to treat the
 * mark as absence. These cases are the readers that did not: catalog links that kept counting as uses
 * of their value, a history that still answered for a table that was gone, and a transition that
 * answered "wrong status" - which confirms the table is there - instead of "not found". The last case
 * is the other side of the same rule: a catalog value that is not accepted is not shown to the people
 * a table is shown to, while the people running the table keep seeing it so that saving never drops it.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TableDeletionIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private GameTableService gameTableService;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterService masterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TableTagRepository tableTagRepository;

    @Autowired
    private TableCatalogService tableCatalogService;

    private User master;
    private User admin;
    private User player;
    private GameTable draft;

    @BeforeEach
    void setUp() {
        master = userRepository.save(new User(randomDiscordId(), "Master"));
        admin = userRepository.save(new User(randomDiscordId(), "Admin"));
        player = userRepository.save(new User(randomDiscordId(), "Player"));
        Role adminRole = roleRepository.findByName(PlatformRole.ADMIN.roleName()).orElseThrow();
        userRoleRepository.save(new UserRole(admin, adminRole));

        draft = gameTableRepository.save(new GameTable("Draft " + randomDiscordId(), master));
        masterService.createPrimary(draft, master);
    }

    /** The table's catalog links fall with it, stamped with the table's own instant, and stop counting as uses. */
    @Test
    void deletingATableUnlinksItsCatalogValuesSoTheyStopCountingAsUses() {
        Tag tag = tag(CatalogStatus.Accepted);
        tableTagRepository.save(new TableTag(draft.getId(), tag.getId()));

        gameTableService.delete(draft.getId(), master.getId());

        assertThat(tableTagRepository.countUsesByValueIds(List.of(tag.getId()))).isEmpty();
        GameTable deleted = gameTableRepository.findById(draft.getId()).orElseThrow();
        assertThat(tableTagRepository.findById_GameTableId(draft.getId()))
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.getStatus()).isEqualTo(TableCatalogLinkStatus.Removed);
                    assertThat(link.getDeletedAt()).isEqualTo(deleted.getDeletedAt());
                });
    }

    /** An admin reads the history of any table - of any table that is there. A deleted one, or one that never existed, is not. */
    @Test
    void theHistoryOfADeletedOrMissingTableIsNotFoundEvenForAnAdmin() {
        gameTableService.delete(draft.getId(), master.getId());

        assertThatThrownBy(() -> gameTableService.getStatusHistory(draft.getId(), admin.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> gameTableService.getStatusHistory("no-such-table", admin.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    /** A transition on a deleted table is a 404, never a "wrong status" 409 that admits the table is there. */
    @Test
    void aTransitionOnADeletedTableIsNotFoundRatherThanAConflict() {
        gameTableService.delete(draft.getId(), master.getId());

        assertThatThrownBy(() -> gameTableService.delete(draft.getId(), admin.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * A disabled value and an unreviewed proposal are not shown to the people the table is shown to;
     * the master still receives them, with their status, so that the form can mark them and saving keeps them.
     */
    @Test
    void aValueThatIsNotAcceptedIsHiddenFromReadersButNotFromTheMaster() {
        GameTable opened = gameTableRepository.findById(draft.getId()).orElseThrow();
        opened.setStatus(GameTableStatus.Opened);
        gameTableRepository.save(opened);
        Tag accepted = tag(CatalogStatus.Accepted);
        Tag disabled = tag(CatalogStatus.Disabled);
        Tag proposed = tag(CatalogStatus.Created);
        for (Tag tag : List.of(accepted, disabled, proposed)) {
            tableTagRepository.save(new TableTag(draft.getId(), tag.getId()));
        }

        assertThat(gameTableService.getDetail(draft.getId(), player.getId()).tags())
                .extracting(CatalogValueResponse::id)
                .containsExactly(accepted.getId());
        assertThat(gameTableService.getManagedDetail(draft.getId(), master.getId()).tags())
                .extracting(CatalogValueResponse::id, CatalogValueResponse::status)
                .containsExactlyInAnyOrder(
                        tuple(accepted.getId(), "Accepted"),
                        tuple(disabled.getId(), "Disabled"),
                        tuple(proposed.getId(), "Created"));
        assertThat(gameTableService.getDetail(draft.getId(), master.getId()).tags())
                .extracting(CatalogValueResponse::id, CatalogValueResponse::status)
                .containsExactlyInAnyOrder(
                        tuple(accepted.getId(), "Accepted"),
                        tuple(disabled.getId(), "Disabled"),
                        tuple(proposed.getId(), "Created"));
    }

    /**
     * A value already linked to the table stays linked when the master saves with the same ids, even
     * after an admin disabled or rejected it - the form shows it marked and saving must not fail on it.
     * Only a value being linked for the first time has to be linkable.
     */
    @Test
    void savingKeepsAnAlreadyLinkedValueThatIsNoLongerAcceptedButRefusesANewOne() {
        Tag kept = tag(CatalogStatus.Accepted);
        Tag rejected = tag(CatalogStatus.Created);
        tableCatalogService.replaceLinks(draft.getId(), CatalogType.TAGS, List.of(kept.getId(), rejected.getId()));
        kept.setStatus(CatalogStatus.Disabled);
        tagRepository.save(kept);
        rejected.setStatus(CatalogStatus.Rejected);
        tagRepository.save(rejected);

        tableCatalogService.replaceLinks(draft.getId(), CatalogType.TAGS, List.of(kept.getId(), rejected.getId()));

        assertThat(tableCatalogService.findLinks(draft.getId(), CatalogType.TAGS))
                .extracting(CatalogValueResponse::id, CatalogValueResponse::status)
                .containsExactlyInAnyOrder(tuple(kept.getId(), "Disabled"), tuple(rejected.getId(), "Rejected"));

        Tag newlyDisabled = tag(CatalogStatus.Disabled);
        List<String> withNewcomer = List.of(kept.getId(), rejected.getId(), newlyDisabled.getId());
        assertThatThrownBy(() -> tableCatalogService.replaceLinks(draft.getId(), CatalogType.TAGS, withNewcomer))
                .isInstanceOf(ConflictException.class);
    }

    private Tag tag(CatalogStatus status) {
        Tag tag = new Tag("Tag " + randomDiscordId());
        tag.setStatus(status);
        return tagRepository.save(tag);
    }

    /** discord_id is VARCHAR(32); a UUID with the dashes stripped fits exactly. */
    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
