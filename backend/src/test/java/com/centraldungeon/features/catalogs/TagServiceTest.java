package com.centraldungeon.features.catalogs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ApiException;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.features.catalogs.dto.AdminCatalogValueResponse;
import com.centraldungeon.features.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.features.tables.MasterService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * What tags add to the shared catalog rules: a group's category and definition, stored only by its
 * canonical entry, read by its aliases, and handed over whenever the group changes head. The rules
 * the three catalogs share are in {@code SystemServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TableTagRepository tableTagRepository;

    @Mock
    private MasterService masterService;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository, tableTagRepository, Mappers.getMapper(CatalogMapper.class), masterService);
        lenient().when(tagRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(tagRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(tagRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(tableTagRepository.countUsesByValueIds(anyCollection())).thenReturn(List.of());
    }

    // -------------------------------------------------------------- defining

    @Test
    void definesACanonicalTag() {
        Tag tag = tag("t-1", "Horror", null, CatalogStatus.Accepted);
        when(tagRepository.findById("t-1")).thenReturn(Optional.of(tag));

        AdminCatalogValueResponse response = tagService.define("t-1", TagCategory.Theme, "  Fear and dread.  ");

        assertThat(response.category()).isEqualTo(TagCategory.Theme);
        assertThat(response.description()).isEqualTo("Fear and dread.");
        assertThat(tag.getDescription()).isEqualTo("Fear and dread.");
    }

    @Test
    void aBlankDescriptionClearsIt() {
        Tag tag = tag("t-1", "Horror", null, CatalogStatus.Accepted);
        tag.setDescription("Old text");
        when(tagRepository.findById("t-1")).thenReturn(Optional.of(tag));

        tagService.define("t-1", null, "   ");

        assertThat(tag.getDescription()).isNull();
        assertThat(tag.getCategory()).isNull();
    }

    @Test
    void anAliasCannotBeDefined() {
        when(tagRepository.findById("alias")).thenReturn(Optional.of(tag("alias", "Terror", "t-1", CatalogStatus.Accepted)));

        assertThatThrownBy(() -> tagService.define("alias", TagCategory.Theme, "x"))
                .isInstanceOf(ConflictException.class)
                .extracting(error -> ((ApiException) error).getErrorCode())
                .isEqualTo(ConflictException.CATALOG_VALUE_NOT_CANONICAL);

        verify(tagRepository, never()).save(any());
    }

    @Test
    void aRejectedTagCannotBeDefined() {
        when(tagRepository.findById("t-1")).thenReturn(Optional.of(tag("t-1", "Spam", null, CatalogStatus.Rejected)));

        assertThatThrownBy(() -> tagService.define("t-1", TagCategory.Theme, "x")).isInstanceOf(ConflictException.class);

        verify(tagRepository, never()).save(any());
    }

    // --------------------------------------------------------------- reading

    @Test
    void anAliasReadsItsCanonicalEntrysDefinition() {
        Tag head = defined(tag("t-1", "Horror", null, CatalogStatus.Accepted), TagCategory.Theme, "Fear.");
        Tag alias = tag("alias", "Terror", "t-1", CatalogStatus.Accepted);
        when(tagRepository.findById("alias")).thenReturn(Optional.of(alias));
        when(tagRepository.findAllById(any())).thenReturn(List.of(head));

        CatalogValueResponse response = tagService.find("alias");

        assertThat(response.category()).isEqualTo(TagCategory.Theme);
        assertThat(response.description()).isEqualTo("Fear.");
    }

    @Test
    void anAliasIgnoresWhateverItStoresItself() {
        Tag head = tag("t-1", "Horror", null, CatalogStatus.Accepted);
        Tag alias = defined(tag("alias", "Terror", "t-1", CatalogStatus.Accepted), TagCategory.Age, "Stale");
        when(tagRepository.findById("alias")).thenReturn(Optional.of(alias));
        when(tagRepository.findAllById(any())).thenReturn(List.of(head));

        CatalogValueResponse response = tagService.find("alias");

        assertThat(response.category()).isNull();
        assertThat(response.description()).isNull();
    }

    // ---------------------------------------------------------- handing over

    @Test
    void mergingHandsTheDefinitionToTheTargetWithoutOverwritingIt() {
        Tag source = defined(tag("src", "DANDD", null, CatalogStatus.Accepted), TagCategory.Theme, "From the source");
        Tag target = defined(tag("dst", "D&D 5e", null, CatalogStatus.Accepted), null, "From the target");
        when(tagRepository.findById("src")).thenReturn(Optional.of(source));
        when(tagRepository.findById("dst")).thenReturn(Optional.of(target));
        when(tagRepository.findByCanonicalId("src")).thenReturn(List.of());

        tagService.merge("src", "dst");

        assertThat(target.getCategory()).isEqualTo(TagCategory.Theme);
        assertThat(target.getDescription()).isEqualTo("From the target");
        assertThat(source.getCategory()).isNull();
        assertThat(source.getDescription()).isNull();
    }

    @Test
    void promotingHandsTheDefinitionToTheNewHead() {
        Tag head = defined(tag("head", "Horror", null, CatalogStatus.Accepted), TagCategory.Theme, "Fear.");
        Tag alias = tag("alias", "Terror", "head", CatalogStatus.Accepted);
        when(tagRepository.findById("alias")).thenReturn(Optional.of(alias));
        when(tagRepository.findById("head")).thenReturn(Optional.of(head));
        when(tagRepository.findByCanonicalId("head")).thenReturn(List.of(alias));

        tagService.promote("alias");

        assertThat(alias.getCategory()).isEqualTo(TagCategory.Theme);
        assertThat(alias.getDescription()).isEqualTo("Fear.");
        assertThat(head.getCategory()).isNull();
        assertThat(head.getDescription()).isNull();
    }

    @Test
    void disablingAHeadHandsTheDefinitionToItsSuccessor() {
        Tag head = defined(tag("head", "Horror", null, CatalogStatus.Accepted), TagCategory.Theme, "Fear.");
        Tag successor = tag("next", "Terror", "head", CatalogStatus.Accepted);
        when(tagRepository.findById("head")).thenReturn(Optional.of(head));
        when(tagRepository.findByCanonicalId("head")).thenReturn(List.of(successor));

        tagService.disable("head", "next");

        assertThat(successor.getCategory()).isEqualTo(TagCategory.Theme);
        assertThat(successor.getDescription()).isEqualTo("Fear.");
        assertThat(head.getCategory()).isNull();
    }

    @Test
    void splittingLeavesTheDefinitionWithTheGroup() {
        Tag head = defined(tag("head", "Horror", null, CatalogStatus.Accepted), TagCategory.Theme, "Fear.");
        Tag alias = tag("alias", "Terror", "head", CatalogStatus.Accepted);
        when(tagRepository.findById("alias")).thenReturn(Optional.of(alias));

        AdminCatalogValueResponse response = tagService.split("alias");

        assertThat(response.category()).isNull();
        assertThat(alias.getCategory()).isNull();
        assertThat(head.getCategory()).isEqualTo(TagCategory.Theme);
    }

    @Test
    void acceptingAProposalIntoAGroupHandsOverWhatItCarried() {
        Tag proposal = defined(tag("p", "Terror", null, CatalogStatus.Created), TagCategory.ContentWarning, "Gore.");
        Tag head = tag("head", "Horror", null, CatalogStatus.Accepted);
        when(tagRepository.findById("p")).thenReturn(Optional.of(proposal));
        when(tagRepository.findById("head")).thenReturn(Optional.of(head));

        tagService.accept("p", "head");

        assertThat(head.getCategory()).isEqualTo(TagCategory.ContentWarning);
        assertThat(proposal.getCategory()).isNull();
        assertThat(proposal.getDescription()).isNull();
    }

    private static Tag tag(String id, String name, @Nullable String canonicalId, CatalogStatus status) {
        Tag tag = new Tag(name);
        ReflectionTestUtils.setField(tag, "id", id);
        ReflectionTestUtils.setField(tag, "createdAt", LocalDateTime.now());
        tag.setCanonicalId(canonicalId);
        tag.setStatus(status);
        return tag;
    }

    private static Tag defined(Tag tag, @Nullable TagCategory category, @Nullable String description) {
        tag.setCategory(category);
        tag.setDescription(description);
        return tag;
    }
}
