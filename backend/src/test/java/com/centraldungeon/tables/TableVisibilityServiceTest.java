package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The single point of F3.4, which is what the whole slice hangs off.
 *
 * <p>§7 of {@code fase-3-admin-owner.md} named the failure mode in advance: «el veto toca seis vías
 * de lectura y <b>es fácil cerrar cinco</b>». It was easy because there were <b>three copies</b> of
 * the same lookup - {@code GameTableService.getEntityById}, whose Javadoc claimed to be «the single
 * lookup every read goes through» and was not, {@code TableSessionService.getTable} and
 * {@code TableTaskService.requireLiveTable}. A rule with three homes is a rule that gets added to
 * two. This class is the one home, and what is pinned here is that the answer is <b>the same</b> for
 * all three ways of not being there: it does not exist, it was deleted, you were vetoed.
 */
@ExtendWith(MockitoExtension.class)
class TableVisibilityServiceTest {

    @Mock
    private GameTableRepository gameTableRepository;

    @Mock
    private TableRegistrationRepository registrationRepository;

    private TableVisibilityService service() {
        return new TableVisibilityService(gameTableRepository, registrationRepository);
    }

    /** The ordinary case: the table is there, the reader is not vetoed, and the table comes back. */
    @Test
    void aLiveTableIsVisibleToSomebodyWhoIsNotVetoed() {
        when(gameTableRepository.findById("t1")).thenReturn(Optional.of(table(GameTableStatus.Opened)));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "t1", "player-1", List.of(TableRegistrationStatus.Blocked)))
                .thenReturn(false);

        assertThat(service().requireVisible("t1", "player-1")).isNotNull();
    }

    /** A table that is not there is a 404, which is what the three copies already did (#25). */
    @Test
    void aTableThatDoesNotExistIs404() {
        when(gameTableRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().requireVisible("ghost", "player-1"))
                .isInstanceOf(NotFoundException.class);
    }

    /** A soft-deleted table does not exist for any read either (#25, #175). */
    @Test
    void aDeletedTableIs404() {
        when(gameTableRepository.findById("t2")).thenReturn(Optional.of(table(GameTableStatus.Deleted)));

        assertThatThrownBy(() -> service().requireVisible("t2", "player-1"))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * <b>The veto is a 404 and never a 403</b> (#29). It is the decision everything else rests on: a
     * 403 confirms exactly what the 404 denies - that the table is there, and that the reader had
     * something to do with it.
     */
    @Test
    void aVetoedTableIs404AndNeverA403() {
        when(gameTableRepository.findById("t3")).thenReturn(Optional.of(table(GameTableStatus.InProgress)));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "t3", "vetoed", List.of(TableRegistrationStatus.Blocked)))
                .thenReturn(true);

        assertThatThrownBy(() -> service().requireVisible("t3", "vetoed"))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * And the three ways of not being there say <b>the same thing</b>. If the veto's message could be
     * told apart from «it does not exist», the 404 would be a 403 with a different number: whoever
     * read the answer would know the table is still there.
     */
    @Test
    void theThreeWaysOfNotBeingThereGiveTheSameAnswer() {
        when(gameTableRepository.findById("absent")).thenReturn(Optional.empty());
        when(gameTableRepository.findById("deleted")).thenReturn(Optional.of(table(GameTableStatus.Deleted)));
        when(gameTableRepository.findById("vetoed")).thenReturn(Optional.of(table(GameTableStatus.Opened)));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "vetoed", "p", List.of(TableRegistrationStatus.Blocked)))
                .thenReturn(true);

        String absent = messageOf("absent");
        String deleted = messageOf("deleted");
        String vetoed = messageOf("vetoed");

        assertThat(absent).isEqualTo("Table not found: absent");
        assertThat(deleted).isEqualTo("Table not found: deleted");
        assertThat(vetoed).isEqualTo("Table not found: vetoed");
    }

    /**
     * {@code requireExisting} does <b>not</b> ask about the veto, and that is exactly what it is: the
     * door for the reads with no reader to veto - a master on their own table, where pertenencia and
     * having applied are disjoint sets (#154). It does not ask the registrations repository once.
     */
    @Test
    void requireExistingDoesNotAskAboutTheVeto() {
        when(gameTableRepository.findById("t4")).thenReturn(Optional.of(table(GameTableStatus.Opened)));

        assertThatCode(() -> service().requireExisting("t4")).doesNotThrowAnyException();
    }

    /** But it still decides what every read shares: a deleted table is there for nobody (#25). */
    @Test
    void requireExistingStillHidesTheDeletedTable() {
        when(gameTableRepository.findById("t5")).thenReturn(Optional.of(table(GameTableStatus.Deleted)));

        assertThatThrownBy(() -> service().requireExisting("t5")).isInstanceOf(NotFoundException.class);
    }

    /** {@code isVetoed} is the loose question, for a caller that already holds the table - {@code FileService} (#206). */
    @Test
    void isVetoedAnswersOnlyForBlocked() {
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "t6", "p", List.of(TableRegistrationStatus.Blocked)))
                .thenReturn(true);

        assertThat(service().isVetoed("t6", "p")).isTrue();
    }

    private String messageOf(String tableId) {
        try {
            service().requireVisible(tableId, "p");
            throw new AssertionError("expected a NotFoundException for " + tableId);
        } catch (NotFoundException expected) {
            return expected.getMessage();
        }
    }

    private static GameTable table(GameTableStatus status) {
        GameTable table = new GameTable("Table", null);
        table.setStatus(status);
        return table;
    }
}
