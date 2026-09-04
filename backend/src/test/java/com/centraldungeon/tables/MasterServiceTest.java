package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MasterServiceTest {

    @Mock
    private MasterRepository masterRepository;

    @Mock
    private TableRegistrationRepository registrationRepository;

    @Mock
    private UserService userService;

    private MasterService masterService;

    @BeforeEach
    void setUp() {
        masterService = new MasterService(masterRepository, registrationRepository, userService);
    }

    @Test
    void rejectsAnActorWhoIsNotAMasterOfTheTable() {
        GameTable table = persistedTable("table-1");
        when(masterRepository.findByGameTableIdForUpdate("table-1")).thenReturn(List.of());

        assertThatThrownBy(() -> masterService.addOrPromote(table, "stranger", "target", MasterType.Secondary))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void rejectsASecondaryTryingToManageMasters() {
        GameTable table = persistedTable("table-2");
        User secondaryUser = persistedUser("secondary-1");
        Master secondary = new Master(table, secondaryUser, MasterType.Secondary);
        when(masterRepository.findByGameTableIdForUpdate("table-2")).thenReturn(List.of(secondary));

        assertThatThrownBy(() -> masterService.addOrPromote(table, "secondary-1", "target", MasterType.Secondary))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void promotesAnExistingSecondaryAndDemotesTheOldPrimary() {
        GameTable table = persistedTable("table-3");
        User primaryUser = persistedUser("primary-1");
        User secondaryUser = persistedUser("secondary-1");
        Master primary = new Master(table, primaryUser, MasterType.Primary);
        Master secondary = new Master(table, secondaryUser, MasterType.Secondary);
        when(masterRepository.findByGameTableIdForUpdate("table-3")).thenReturn(List.of(primary, secondary));

        masterService.addOrPromote(table, "primary-1", "secondary-1", MasterType.Primary);

        assertThat(primary.getMasterType()).isEqualTo(MasterType.Secondary);
        assertThat(secondary.getMasterType()).isEqualTo(MasterType.Primary);
    }

    @Test
    void addsABrandNewSecondaryMaster() {
        GameTable table = persistedTable("table-4");
        User primaryUser = persistedUser("primary-1");
        Master primary = new Master(table, primaryUser, MasterType.Primary);
        when(masterRepository.findByGameTableIdForUpdate("table-4")).thenReturn(List.of(primary));
        User newTargetUser = persistedUser("new-target");
        when(userService.getById("new-target")).thenReturn(newTargetUser);
        when(masterRepository.save(any(Master.class))).thenAnswer(invocation -> invocation.getArgument(0));

        masterService.addOrPromote(table, "primary-1", "new-target", MasterType.Secondary);

        assertThat(primary.getMasterType()).isEqualTo(MasterType.Primary);
    }

    @Test
    void rejectsAddingAPlayerOfTheTableAsItsMaster() {
        GameTable table = persistedTable("table-6");
        User primaryUser = persistedUser("primary-1");
        Master primary = new Master(table, primaryUser, MasterType.Primary);
        when(masterRepository.findByGameTableIdForUpdate("table-6")).thenReturn(List.of(primary));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "table-6", "player-1", List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player)))
                .thenReturn(true);

        assertThatThrownBy(() -> masterService.addOrPromote(table, "primary-1", "player-1", MasterType.Secondary))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void isPrimaryOfIsFalseWhenThereIsNoMasterRow() {
        when(masterRepository.findByGameTable_IdAndUser_IdAndStatus("table-5", "nobody", MasterRowStatus.Created))
                .thenReturn(Optional.empty());

        assertThat(masterService.isPrimaryOf("table-5", "nobody")).isFalse();
    }

    /**
     * The row of a removed co-master survives as a record (#175), and membership has to stop
     * counting it - otherwise removing somebody would take away their screens and leave their
     * permissions.
     */
    @Test
    void isMasterOfIgnoresARowThatWasRemoved() {
        when(masterRepository.findByGameTable_IdAndUser_IdAndStatus("table-7", "removed", MasterRowStatus.Created))
                .thenReturn(Optional.empty());

        assertThat(masterService.isMasterOf("table-7", "removed")).isFalse();
    }

    @Test
    void removesACoMaster() {
        GameTable table = persistedTable("table-8");
        Master primary = new Master(table, persistedUser("primary-1"), MasterType.Primary);
        Master secondary = new Master(table, persistedUser("secondary-1"), MasterType.Secondary);
        when(masterRepository.findByGameTableIdForUpdate("table-8")).thenReturn(List.of(primary, secondary));

        masterService.removeMaster(table, "primary-1", "secondary-1");

        assertThat(secondary.getStatus()).isEqualTo(MasterRowStatus.Deleted);
        assertThat(primary.getStatus()).isEqualTo(MasterRowStatus.Created);
    }

    @Test
    void rejectsASecondaryTryingToRemoveAnotherMaster() {
        GameTable table = persistedTable("table-9");
        Master primary = new Master(table, persistedUser("primary-1"), MasterType.Primary);
        Master secondary = new Master(table, persistedUser("secondary-1"), MasterType.Secondary);
        Master other = new Master(table, persistedUser("secondary-2"), MasterType.Secondary);
        when(masterRepository.findByGameTableIdForUpdate("table-9")).thenReturn(List.of(primary, secondary, other));

        assertThatThrownBy(() -> masterService.removeMaster(table, "secondary-1", "secondary-2"))
                .isInstanceOf(ForbiddenActionException.class);
        assertThat(other.getStatus()).isEqualTo(MasterRowStatus.Created);
    }

    /** A table with nobody in charge has nobody authorized to run it: hand the role over first (#73). */
    @Test
    void refusesToRemoveThePrimary() {
        GameTable table = persistedTable("table-10");
        Master primary = new Master(table, persistedUser("primary-1"), MasterType.Primary);
        when(masterRepository.findByGameTableIdForUpdate("table-10")).thenReturn(List.of(primary));

        assertThatThrownBy(() -> masterService.removeMaster(table, "primary-1", "primary-1")).isInstanceOf(ConflictException.class);
        assertThat(primary.getStatus()).isEqualTo(MasterRowStatus.Created);
    }

    @Test
    void refusesToRemoveSomebodyWhoDoesNotRunTheTable() {
        GameTable table = persistedTable("table-11");
        Master primary = new Master(table, persistedUser("primary-1"), MasterType.Primary);
        when(masterRepository.findByGameTableIdForUpdate("table-11")).thenReturn(List.of(primary));

        assertThatThrownBy(() -> masterService.removeMaster(table, "primary-1", "stranger")).isInstanceOf(ConflictException.class);
    }

    /**
     * The composite key is (table, user), so adding back somebody who was removed cannot insert a
     * second row - it has to bring the first one back.
     */
    @Test
    void revivesTheRowOfSomebodyWhoHadBeenRemoved() {
        GameTable table = persistedTable("table-12");
        Master primary = new Master(table, persistedUser("primary-1"), MasterType.Primary);
        Master removed = new Master(table, persistedUser("secondary-1"), MasterType.Secondary);
        removed.setStatus(MasterRowStatus.Deleted);
        removed.setDeletedAt(LocalDateTime.now());
        when(masterRepository.findByGameTableIdForUpdate("table-12")).thenReturn(List.of(primary, removed));

        masterService.addOrPromote(table, "primary-1", "secondary-1", MasterType.Secondary);

        assertThat(removed.getStatus()).isEqualTo(MasterRowStatus.Created);
        assertThat(removed.getMasterType()).isEqualTo(MasterType.Secondary);
        verify(masterRepository, never()).save(any(Master.class));
    }

    /** A removed Primary must not be able to keep managing the table it was taken off. */
    @Test
    void rejectsARemovedMasterActing() {
        GameTable table = persistedTable("table-13");
        Master removed = new Master(table, persistedUser("primary-1"), MasterType.Primary);
        removed.setStatus(MasterRowStatus.Deleted);
        when(masterRepository.findByGameTableIdForUpdate("table-13")).thenReturn(List.of(removed));

        assertThatThrownBy(() -> masterService.addOrPromote(table, "primary-1", "target", MasterType.Secondary))
                .isInstanceOf(ForbiddenActionException.class);
    }

    private GameTable persistedTable(String id) {
        User creator = persistedUser("creator-of-" + id);
        GameTable table = new GameTable("Test table", creator);
        ReflectionTestUtils.setField(table, "id", id);
        return table;
    }

    private User persistedUser(String id) {
        User user = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
