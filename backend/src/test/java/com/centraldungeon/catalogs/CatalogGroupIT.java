package com.centraldungeon.catalogs;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.catalogs.dto.AdminCatalogValueResponse;
import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.common.model.PageResponse;
import java.util.List;
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
 * What a mocked repository cannot prove about the catalogs: that the visibility filter of #57 is
 * really in the SQL, that a merge leaves the flat depth-1 shape of #59 intact against a real FK, and
 * that resolving a group works from either end of it (#54, #56).
 *
 * <p>The last one is the scenario F1.1 is measured by: an admin merges "DANDD" into "D&D 5e" and the
 * explorer finds the tables of both, <b>without a single row of table_systems being rewritten</b>.
 *
 * <p>Wired with @DynamicPropertySource, not @ServiceConnection: see RegistrationServiceIT for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class CatalogGroupIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 50, Sort.by("name"));

    @Autowired
    private TagService tagService;

    @Autowired
    private TagRepository tagRepository;

    private String dnd;
    private String dandd;
    private String pending;
    private String disabled;

    @BeforeEach
    void setUp() {
        clearTags();
        dnd = accepted("D&D 5e", null);
        dandd = accepted("DANDD", null);
        pending = save(new Tag("Todavía sin revisar"), CatalogStatus.Created, null);
        disabled = save(new Tag("Fuera de circulación"), CatalogStatus.Disabled, null);
    }

    /** The whole point of #57 and #81: neither a proposal nor a disabled value reaches a player. */
    @Test
    void showsOnlyAcceptedValuesToEveryoneButAnAdmin() {
        assertThat(namesOf(tagService.search(null, FIRST_PAGE))).containsExactlyInAnyOrder("D&D 5e", "DANDD");
    }

    @Test
    void showsEverythingToAnAdminWhoseJobIsToReviewIt() {
        PageResponse<AdminCatalogValueResponse> page = tagService.adminSearch(null, List.of(), FIRST_PAGE);

        assertThat(page.content()).extracting(AdminCatalogValueResponse::name)
                .containsExactlyInAnyOrder("D&D 5e", "DANDD", "Todavía sin revisar", "Fuera de circulación");
    }

    @Test
    void narrowsTheAdminListToOneStatus() {
        PageResponse<AdminCatalogValueResponse> page =
                tagService.adminSearch(null, List.of(CatalogStatus.Created), FIRST_PAGE);

        assertThat(page.content()).extracting(AdminCatalogValueResponse::name).containsExactly("Todavía sin revisar");
    }

    /** Free text matches the name, case-insensitively, and the search box never blows up on a typo. */
    @Test
    void searchesByNameFromTheOneQueryParameter() {
        assertThat(namesOf(tagService.search("dandd", FIRST_PAGE))).containsExactly("DANDD");
        assertThat(namesOf(tagService.search("/name d&d", FIRST_PAGE))).containsExactly("D&D 5e");
        // An unknown /field is searched as literal text, never answered with a 400 (#164).
        assertThat(namesOf(tagService.search("/nombre d&d", FIRST_PAGE))).isEmpty();
    }

    /** The scenario F1.1 closes on (#54, #56): merge, then find the group from either member. */
    @Test
    void resolvesTheMergedGroupFromEitherOfItsNames() {
        assertThat(tagService.resolveGroupIds(dandd)).containsExactly(dandd);

        tagService.merge(dandd, dnd);

        assertThat(tagService.resolveGroupIds(dandd)).containsExactlyInAnyOrder(dnd, dandd);
        assertThat(tagService.resolveGroupIds(dnd)).containsExactlyInAnyOrder(dnd, dandd);
    }

    /** #59 against a real self-referencing FK: after a merge every alias still points at a canonical entry. */
    @Test
    void keepsGroupsOneLevelDeepAfterAMerge() {
        String alias = accepted("DND", dandd);

        tagService.merge(dandd, dnd);

        assertThat(canonicalIdOf(alias)).isEqualTo(dnd);
        assertThat(canonicalIdOf(dandd)).isEqualTo(dnd);
        assertThat(canonicalIdOf(dnd)).isNull();
    }

    @Test
    void splittingAnAliasTakesItOutOfTheGroup() {
        tagService.merge(dandd, dnd);

        tagService.split(dandd);

        assertThat(canonicalIdOf(dandd)).isNull();
        assertThat(tagService.resolveGroupIds(dnd)).containsExactly(dnd);
    }

    /** Restoring puts everything back with no migration (#81). */
    @Test
    void disablingAndRestoringLeavesTheGroupAsItWas() {
        tagService.merge(dandd, dnd);

        tagService.disable(dandd, null);
        assertThat(tagService.resolveGroupIds(dnd)).containsExactly(dnd);

        tagService.restore(dandd);
        assertThat(tagService.resolveGroupIds(dnd)).containsExactlyInAnyOrder(dnd, dandd);
    }

    /** Accepting a proposal into a group is the other half of #55, and it is one round trip. */
    @Test
    void acceptingAProposalIntoAGroupMakesItSearchableAsTheGroup() {
        tagService.accept(pending, dnd);

        assertThat(namesOf(tagService.search(null, FIRST_PAGE)))
                .containsExactlyInAnyOrder("D&D 5e", "DANDD", "Todavía sin revisar");
        assertThat(tagService.resolveGroupIds(dnd)).containsExactlyInAnyOrder(dnd, pending);
    }

    /** The group read the admin screen needs: every member, canonical first, whatever its status. */
    @Test
    void readsAWholeGroupFromAnyMemberWithTheCanonicalFirst() {
        String alias = accepted("DND", dandd);
        tagService.merge(dandd, dnd);

        List<AdminCatalogValueResponse> group = tagService.group(alias);

        assertThat(group).extracting(AdminCatalogValueResponse::name).containsExactly("D&D 5e", "DANDD", "DND");
        assertThat(group.getFirst().canonicalId()).isNull();
    }

    /** Unlike the search's view of a group, this one keeps what is not accepted - it is what gets restored. */
    @Test
    void keepsNonAcceptedMembersInTheAdminGroupRead() {
        tagService.accept(pending, dnd);
        tagService.disable(pending, null);

        assertThat(tagService.group(dnd)).extracting(AdminCatalogValueResponse::name).contains("Todavía sin revisar");
        assertThat(tagService.resolveGroupIds(dnd)).containsExactly(dnd);
    }

    @Test
    void aDisabledValueIsStillThereForAnAdminToRestore() {
        assertThat(tagService.find(disabled).status()).isEqualTo(CatalogStatus.Disabled.name());
    }

    /**
     * {@code canonical_id} is a real self-referencing FK, so a plain deleteAll() removes rows in an
     * order MySQL rejects the moment a group survives from the previous test. Flattening the groups
     * first is the fixture's job - and the constraint failing here is itself a small proof that the
     * database enforces what {@link AbstractCatalogService} promises.
     */
    private void clearTags() {
        List<Tag> all = tagRepository.findAll();
        all.forEach(tag -> tag.setCanonicalId(null));
        tagRepository.saveAll(all);
        tagRepository.deleteAll();
    }

    private String accepted(String name, String canonicalId) {
        return save(new Tag(name), CatalogStatus.Accepted, canonicalId);
    }

    private String save(Tag tag, CatalogStatus status, String canonicalId) {
        tag.setStatus(status);
        tag.setCanonicalId(canonicalId);
        return tagRepository.save(tag).getId();
    }

    private String canonicalIdOf(String id) {
        return tagRepository.findById(id).orElseThrow().getCanonicalId();
    }

    private static List<String> namesOf(PageResponse<CatalogValueResponse> page) {
        return page.content().stream().map(CatalogValueResponse::name).toList();
    }
}
