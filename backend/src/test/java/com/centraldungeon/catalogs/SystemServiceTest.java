package com.centraldungeon.catalogs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.catalogs.dto.AdminCatalogValueResponse;
import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The rules of {@link AbstractCatalogService}, exercised through the concrete service that was
 * written first. There is no TagServiceTest or PlatformServiceTest that repeats these: the three
 * subclasses differ only in which bridge table counts their uses, and that difference carries no
 * rule of its own - all three would be testing this same base, three times over.
 */
@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @Mock
    private SystemRepository systemRepository;

    @Mock
    private TableSystemRepository tableSystemRepository;

    private SystemService systemService;

    @BeforeEach
    void setUp() {
        systemService = new SystemService(systemRepository, tableSystemRepository, Mappers.getMapper(CatalogMapper.class));
        // Stands in for @PrePersist: without it a freshly proposed value comes back with no id.
        lenient().when(systemRepository.save(any())).thenAnswer(invocation -> {
            GameSystem saved = invocation.getArgument(0);
            if (ReflectionTestUtils.getField(saved, "id") == null) {
                ReflectionTestUtils.setField(saved, "id", "generated-id");
                ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.now());
            }
            return saved;
        });
        lenient().when(systemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(systemRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(tableSystemRepository.countUsesByValueIds(anyCollection())).thenReturn(List.of());
    }

    // ------------------------------------------------------------- proposing

    @Test
    void proposesAValueAsPendingReview() {
        when(systemRepository.findByNameIgnoreCase("Blades in the Dark")).thenReturn(Optional.empty());

        CatalogValueResponse response = systemService.propose("Blades in the Dark");

        assertThat(response.name()).isEqualTo("Blades in the Dark");
        assertThat(response.status()).isEqualTo(CatalogStatus.Created.name());
    }

    @Test
    void rejectsAProposalWhoseNameAlreadyExistsRegardlessOfCase() {
        when(systemRepository.findByNameIgnoreCase("d&d 5e")).thenReturn(Optional.of(value("s-1", "D&D 5e", null, CatalogStatus.Accepted)));

        assertThatThrownBy(() -> systemService.propose("d&d 5e"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("D&D 5e");

        verify(systemRepository, never()).save(any());
    }

    @Test
    void stripsSurroundingWhitespaceFromAProposedName() {
        when(systemRepository.findByNameIgnoreCase("Fate Core")).thenReturn(Optional.empty());

        assertThat(systemService.propose("  Fate Core  ").name()).isEqualTo("Fate Core");
    }

    // -------------------------------------------------------------- accepting

    @Test
    void acceptsAProposalAsANewCanonicalEntry() {
        GameSystem proposed = value("s-1", "Fate Core", null, CatalogStatus.Created);
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(proposed));

        AdminCatalogValueResponse response = systemService.accept("s-1", null);

        assertThat(response.status()).isEqualTo(CatalogStatus.Accepted.name());
        assertThat(response.canonicalId()).isNull();
    }

    @Test
    void acceptsAProposalAsAnAliasOfAnExistingGroup() {
        GameSystem proposed = value("s-2", "DANDD", null, CatalogStatus.Created);
        GameSystem canonical = value("s-1", "D&D 5e", null, CatalogStatus.Accepted);
        when(systemRepository.findById("s-2")).thenReturn(Optional.of(proposed));
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(canonical));
        when(systemRepository.findAllById(any())).thenReturn(List.of(canonical));

        AdminCatalogValueResponse response = systemService.accept("s-2", "s-1");

        assertThat(response.canonicalId()).isEqualTo("s-1");
        assertThat(response.canonicalName()).isEqualTo("D&D 5e");
    }

    /** Depth is always 1 (#59): pointing at an alias would build the second level that makes cycles possible. */
    @Test
    void rejectsAClassificationWhoseTargetIsItselfAnAlias() {
        GameSystem proposed = value("s-3", "DND", null, CatalogStatus.Created);
        GameSystem alias = value("s-2", "DANDD", "s-1", CatalogStatus.Accepted);
        when(systemRepository.findById("s-3")).thenReturn(Optional.of(proposed));
        when(systemRepository.findById("s-2")).thenReturn(Optional.of(alias));

        assertThatThrownBy(() -> systemService.accept("s-3", "s-2"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("one level deep");
    }

    @Test
    void rejectsAValuePointedAtItself() {
        GameSystem proposed = value("s-1", "Fate Core", null, CatalogStatus.Created);
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(proposed));

        assertThatThrownBy(() -> systemService.accept("s-1", "s-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("its own canonical entry");
    }

    @Test
    void rejectsAClassificationIntoAGroupThatIsNotInCirculation() {
        GameSystem proposed = value("s-2", "DANDD", null, CatalogStatus.Created);
        GameSystem disabled = value("s-1", "D&D 5e", null, CatalogStatus.Disabled);
        when(systemRepository.findById("s-2")).thenReturn(Optional.of(proposed));
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> systemService.accept("s-2", "s-1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsAcceptingSomethingAlreadyAccepted() {
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(value("s-1", "D&D 5e", null, CatalogStatus.Accepted)));

        assertThatThrownBy(() -> systemService.accept("s-1", null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void pointsAtRestoreWhenAskedToAcceptADisabledValue() {
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(value("s-1", "D&D 5e", null, CatalogStatus.Disabled)));

        assertThatThrownBy(() -> systemService.accept("s-1", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("restore it instead");
    }

    // -------------------------------------------------------------- rejecting

    @Test
    void rejectsAPendingProposal() {
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(value("s-1", "Dnd5", null, CatalogStatus.Created)));

        assertThat(systemService.reject("s-1").status()).isEqualTo(CatalogStatus.Rejected.name());
    }

    /** Rejecting and disabling are different statements about a value, and not interchangeable. */
    @Test
    void refusesToRejectAValueAlreadyInCirculation() {
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(value("s-1", "D&D 5e", null, CatalogStatus.Accepted)));

        assertThatThrownBy(() -> systemService.reject("s-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("only a pending proposal");
    }

    // ----------------------------------------------------------- merge, split

    @Test
    void mergingMovesTheWholeSourceGroupIncludingItsAliases() {
        GameSystem source = value("s-1", "DANDD", null, CatalogStatus.Accepted);
        GameSystem sourceAlias = value("s-2", "DND", "s-1", CatalogStatus.Accepted);
        GameSystem target = value("s-9", "D&D 5e", null, CatalogStatus.Accepted);
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(source));
        when(systemRepository.findById("s-9")).thenReturn(Optional.of(target));
        when(systemRepository.findByCanonicalId("s-1")).thenReturn(List.of(sourceAlias));

        AdminCatalogValueResponse response = systemService.merge("s-1", "s-9");

        assertThat(source.getCanonicalId()).isEqualTo("s-9");
        assertThat(sourceAlias.getCanonicalId()).isEqualTo("s-9");
        assertThat(response.id()).isEqualTo("s-9");
        assertThat(response.canonicalId()).isNull();
    }

    @Test
    void rejectsMergingAGroupIntoItself() {
        assertThatThrownBy(() -> systemService.merge("s-1", "s-1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsMergingWhenTheSourceIsAnAliasRatherThanAGroup() {
        GameSystem alias = value("s-2", "DND", "s-1", CatalogStatus.Accepted);
        GameSystem target = value("s-9", "D&D 5e", null, CatalogStatus.Accepted);
        when(systemRepository.findById("s-2")).thenReturn(Optional.of(alias));
        when(systemRepository.findById("s-9")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> systemService.merge("s-2", "s-9"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("source");
    }

    @Test
    void rejectsMergingIntoAnAlias() {
        GameSystem source = value("s-1", "DANDD", null, CatalogStatus.Accepted);
        GameSystem alias = value("s-2", "DND", "s-9", CatalogStatus.Accepted);
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(source));
        when(systemRepository.findById("s-2")).thenReturn(Optional.of(alias));

        assertThatThrownBy(() -> systemService.merge("s-1", "s-2"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("target");
    }

    @Test
    void takesAnAliasOutOfItsGroup() {
        GameSystem alias = value("s-2", "DND", "s-1", CatalogStatus.Accepted);
        when(systemRepository.findById("s-2")).thenReturn(Optional.of(alias));

        AdminCatalogValueResponse response = systemService.split("s-2");

        assertThat(alias.getCanonicalId()).isNull();
        assertThat(response.canonicalId()).isNull();
    }

    @Test
    void rejectsSplittingAValueThatIsAlreadyItsOwnGroup() {
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(value("s-1", "D&D 5e", null, CatalogStatus.Accepted)));

        assertThatThrownBy(() -> systemService.split("s-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no group to leave");
    }

    // -------------------------------------------------------- disable, restore

    @Test
    void disablesAValueWithoutTouchingItsLinks() {
        GameSystem value = value("s-1", "GURPS", null, CatalogStatus.Accepted);
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(value));
        when(systemRepository.findByCanonicalId("s-1")).thenReturn(List.of());

        AdminCatalogValueResponse response = systemService.disable("s-1", null);

        assertThat(response.status()).isEqualTo(CatalogStatus.Disabled.name());
        assertThat(value.getDeletedAt()).isNotNull();
        verify(tableSystemRepository, never()).deleteAll();
    }

    /** Disabling a canonical entry that still has aliases is changing the group's canonical (#59). */
    @Test
    void refusesToDisableACanonicalEntryOfALiveGroupWithoutASuccessor() {
        GameSystem canonical = value("s-1", "D&D 5e", null, CatalogStatus.Accepted);
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(canonical));
        when(systemRepository.findByCanonicalId("s-1")).thenReturn(List.of(value("s-2", "DND", "s-1", CatalogStatus.Accepted)));

        assertThatThrownBy(() -> systemService.disable("s-1", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("pick which one takes over");
    }

    @Test
    void handsTheGroupToTheSuccessorTheAdminPicked() {
        GameSystem canonical = value("s-1", "D&D 5e", null, CatalogStatus.Accepted);
        GameSystem successor = value("s-2", "DND", "s-1", CatalogStatus.Accepted);
        GameSystem other = value("s-3", "DANDD", "s-1", CatalogStatus.Accepted);
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(canonical));
        when(systemRepository.findByCanonicalId("s-1")).thenReturn(List.of(successor, other));

        systemService.disable("s-1", "s-2");

        assertThat(successor.getCanonicalId()).isNull();
        assertThat(other.getCanonicalId()).isEqualTo("s-2");
        // The disabled value stays in the group, so the tables tagged with it stay findable by it.
        assertThat(canonical.getCanonicalId()).isEqualTo("s-2");
        assertThat(canonical.getStatus()).isEqualTo(CatalogStatus.Disabled);
    }

    @Test
    void rejectsASuccessorThatIsNotAMemberOfTheGroup() {
        GameSystem canonical = value("s-1", "D&D 5e", null, CatalogStatus.Accepted);
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(canonical));
        when(systemRepository.findByCanonicalId("s-1")).thenReturn(List.of(value("s-2", "DND", "s-1", CatalogStatus.Accepted)));

        assertThatThrownBy(() -> systemService.disable("s-1", "s-99"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("live member");
    }

    @Test
    void restoringPutsTheValueBackInTheGroupItWasIn() {
        GameSystem disabled = value("s-2", "DND", "s-1", CatalogStatus.Disabled);
        disabled.setDeletedAt(LocalDateTime.now());
        when(systemRepository.findById("s-2")).thenReturn(Optional.of(disabled));

        AdminCatalogValueResponse response = systemService.restore("s-2");

        assertThat(response.status()).isEqualTo(CatalogStatus.Accepted.name());
        assertThat(response.canonicalId()).isEqualTo("s-1");
        assertThat(disabled.getDeletedAt()).isNull();
    }

    @Test
    void rejectsRestoringSomethingThatIsNotDisabled() {
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(value("s-1", "D&D 5e", null, CatalogStatus.Accepted)));

        assertThatThrownBy(() -> systemService.restore("s-1")).isInstanceOf(ConflictException.class);
    }

    // --------------------------------------------------------- group resolution

    /** #54, #56: searching by any member has to return the whole group, from either direction. */
    @Test
    void resolvesTheWholeGroupFromAnyOfItsMembers() {
        GameSystem canonical = value("s-1", "D&D 5e", null, CatalogStatus.Accepted);
        GameSystem alias = value("s-2", "DANDD", "s-1", CatalogStatus.Accepted);
        when(systemRepository.findById("s-2")).thenReturn(Optional.of(alias));
        when(systemRepository.findByIdOrCanonicalId("s-1", "s-1")).thenReturn(List.of(canonical, alias));

        assertThat(systemService.resolveGroupIds("s-2")).containsExactlyInAnyOrder("s-1", "s-2");
    }

    /** A disabled or unreviewed synonym does not filter (#57, #81), so it is not part of the group a search resolves. */
    @Test
    void leavesOutGroupMembersThatAreNotAccepted() {
        GameSystem canonical = value("s-1", "D&D 5e", null, CatalogStatus.Accepted);
        GameSystem pending = value("s-2", "DANDD", "s-1", CatalogStatus.Created);
        GameSystem disabled = value("s-3", "DND", "s-1", CatalogStatus.Disabled);
        when(systemRepository.findById("s-1")).thenReturn(Optional.of(canonical));
        when(systemRepository.findByIdOrCanonicalId("s-1", "s-1")).thenReturn(List.of(canonical, pending, disabled));

        assertThat(systemService.resolveGroupIds("s-1")).containsExactly("s-1");
    }

    @Test
    void namesWhatWasNotFound() {
        when(systemRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemService.find("nope"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("System nope");
    }

    private static GameSystem value(String id, String name, String canonicalId, CatalogStatus status) {
        GameSystem system = new GameSystem(name);
        ReflectionTestUtils.setField(system, "id", id);
        ReflectionTestUtils.setField(system, "createdAt", LocalDateTime.now());
        system.setCanonicalId(canonicalId);
        system.setStatus(status);
        return system;
    }
}
