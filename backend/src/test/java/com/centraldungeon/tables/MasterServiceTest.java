package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.util.List;
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
        when(masterRepository.findByGameTable_IdAndUser_Id("table-5", "nobody")).thenReturn(java.util.Optional.empty());

        assertThat(masterService.isPrimaryOf("table-5", "nobody")).isFalse();
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
