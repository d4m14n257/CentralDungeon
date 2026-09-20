package com.centraldungeon.approvals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.users.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The half of {@code entity_type}/{@code entity_id} that the database cannot do (#78).
 *
 * <p>The interesting case is the last one. An entity type with no case here used to answer
 * {@code false}, which never lied in the dangerous direction - nothing was ever treated as verified
 * when it could not be - but gave the wrong diagnosis, and the wrong diagnosis has a cost of its own:
 * every {@code submit} of the new type would answer "cannot open a request about a missing
 * game_table", sending somebody to look for a deleted table rather than at the incomplete switch, and
 * the sweep would log a WARN for every row of that type for ever, indistinguishable from a real
 * orphan. Which is the exact signal the sweep exists to produce, drowned by the bug.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalEntityResolverTest {

    @Mock
    private UserRepository userRepository;

    /** {@code game_table}, added by F3.4 with the flow that produces it (#32). */
    @Mock
    private GameTableRepository gameTableRepository;

    /** {@code table_registration}, same (#39). */
    @Mock
    private TableRegistrationRepository registrationRepository;

    private ApprovalEntityResolver resolver() {
        return new ApprovalEntityResolver(userRepository, gameTableRepository, registrationRepository);
    }

    @Test
    void aUserThatExistsResolves() {
        when(userRepository.existsById("user-1")).thenReturn(true);

        assertThat(resolver().exists(ApprovalEntityResolver.USER, "user-1")).isTrue();
    }

    @Test
    void aDeletedUserDoesNotResolve() {
        when(userRepository.existsById("user-borrado")).thenReturn(false);

        assertThat(resolver().exists(ApprovalEntityResolver.USER, "user-borrado")).isFalse();
    }

    /**
     * A type with no case is a programming error and is named as one. It said {@code game_table} until
     * F3.4 added it along with its flow, which is exactly what the test was asking for; it stays alive
     * with a type nobody emits, because what it protects is the next one.
     */
    @Test
    void anUnknownEntityTypeFailsSayingItIsUnknown() {
        assertThatThrownBy(() -> resolver().exists("comment", "comentario-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown approval entity type")
                .hasMessageContaining("comment");
    }

    /**
     * {@code game_table}, the first of the two F3.4 added (#32). The case exists, so it does not throw
     * - and if somebody removed it, the test above would not notice: this one does.
     */
    @Test
    void aTableThatExistsResolves() {
        GameTable table = new GameTable("Mesa", null);
        table.setStatus(GameTableStatus.InProgress);
        when(gameTableRepository.findById("mesa-1")).thenReturn(Optional.of(table));

        assertThat(resolver().exists(ApprovalEntityResolver.GAME_TABLE, "mesa-1")).isTrue();
    }

    /**
     * A soft-deleted table <b>counts as absent</b>, which is the question F3.2 left open and the F3.4
     * contract closed: the resolver reads the way the rest of the application reads (#25). It is also
     * the first case in which {@code REQUEST_ENTITY_GONE} is genuinely reachable.
     */
    @Test
    void aDeletedTableCountsAsAbsent() {
        GameTable table = new GameTable("Mesa", null);
        table.setStatus(GameTableStatus.Deleted);
        when(gameTableRepository.findById("mesa-borrada")).thenReturn(Optional.of(table));

        assertThat(resolver().exists(ApprovalEntityResolver.GAME_TABLE, "mesa-borrada")).isFalse();
    }

    /** {@code table_registration}, the second (#39), with the same soft-delete rule. */
    @Test
    void aDeletedRegistrationCountsAsAbsent() {
        TableRegistration live = new TableRegistration(null, null, null);
        TableRegistration gone = new TableRegistration(null, null, null);
        gone.setStatus(TableRegistrationStatus.Deleted);
        when(registrationRepository.findById("reg-viva")).thenReturn(Optional.of(live));
        when(registrationRepository.findById("reg-borrada")).thenReturn(Optional.of(gone));

        assertThat(resolver().exists(ApprovalEntityResolver.TABLE_REGISTRATION, "reg-viva")).isTrue();
        assertThat(resolver().exists(ApprovalEntityResolver.TABLE_REGISTRATION, "reg-borrada")).isFalse();
    }
}
