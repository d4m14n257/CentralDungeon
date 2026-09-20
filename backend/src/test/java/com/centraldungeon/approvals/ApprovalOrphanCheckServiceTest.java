package com.centraldungeon.approvals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.users.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The third thing the polymorphic reference of #78 costs, and the one with no other test behind it.
 *
 * <p>{@code docs/fase-3-admin-owner.md} 7: «dos cosas van con ella o no va: la validación en el
 * service antes de insertar, y el chequeo periódico de huérfanas. Sin la segunda, el problema aparece
 * meses después y sin forma de reconstruir qué apuntaba a qué». A job nobody tests is a job nobody
 * knows runs - and this one produces no rows, no response and no side effect, so nothing else in the
 * system would ever notice it had stopped working.
 *
 * <p>What is asserted is the contract around the sweep: which rows it looks at, that it only reports,
 * and that it touches nothing (#25, #126 - the row is evidence, and deleting it automatically is how
 * the evidence is lost).
 */
@ExtendWith(MockitoExtension.class)
class ApprovalOrphanCheckServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private ApprovalEntityResolver entityResolver;

    private ApprovalOrphanCheckService checker() {
        return new ApprovalOrphanCheckService(approvalRequestRepository, entityResolver);
    }

    @Test
    void itCountsTheRequestsWhoseReferenceNoLongerResolves() {
        ApprovalRequest live = request("req-1", "user-1");
        ApprovalRequest orphan = request("req-2", "user-borrado");
        when(approvalRequestRepository.findUnresolved(eq(ApprovalStatus.Pending), any())).thenReturn(List.of(live, orphan));
        when(entityResolver.exists("user", "user-1")).thenReturn(true);
        when(entityResolver.exists("user", "user-borrado")).thenReturn(false);

        assertThat(checker().reportOrphans()).isEqualTo(1);
    }

    /**
     * Pending ones only. If one approved two years ago points at something deleted there is nothing to
     * be done about it; an open one pointing at nothing is a request an admin will not be able to
     * resolver — le responde {@code REQUEST_ENTITY_GONE}.
     */
    @Test
    void itSweepsOnlyTheUnresolvedRequests() {
        when(approvalRequestRepository.findUnresolved(any(), any())).thenReturn(List.of());

        checker().reportOrphans();

        verify(approvalRequestRepository).findUnresolved(eq(ApprovalStatus.Pending), any(Pageable.class));
    }

    /**
     * It neither deletes nor resolves. An automatic delete over data that has already lost its anchor is
     * exactly how the evidence of what pointed at what gets lost (#25, #126).
     */
    @Test
    void reportaYNoTocaNada() {
        ApprovalRequest orphan = request("req-2", "user-borrado");
        when(approvalRequestRepository.findUnresolved(any(), any())).thenReturn(List.of(orphan));
        when(entityResolver.exists("user", "user-borrado")).thenReturn(false);

        checker().reportOrphans();

        assertThat(orphan.getStatus()).isEqualTo(ApprovalStatus.Pending);
        assertThat(orphan.getResolvedAt()).isNull();
        verify(approvalRequestRepository, never()).delete(any(ApprovalRequest.class));
        verify(approvalRequestRepository, never()).save(any());
    }

    /**
     * An entity type with no case in the resolver is a bug in the code, not an orphan — and it must not
     * cost the report on all the others. The pass survives, does not count it as an orphan (it is not
     * one: nobody knows what it is) and carries on with the rest.
     */
    @Test
    void anUnknownTypeNeitherKillsThePassNorCountsAsAnOrphan() {
        ApprovalRequest desconocida = request("req-3", "mesa-1");
        ReflectionTestUtils.setField(desconocida, "entityType", "game_table");
        ApprovalRequest orphan = request("req-2", "user-borrado");
        when(approvalRequestRepository.findUnresolved(any(), any())).thenReturn(List.of(desconocida, orphan));
        when(entityResolver.exists("game_table", "mesa-1"))
                .thenThrow(new IllegalStateException("Unknown approval entity type: game_table"));
        when(entityResolver.exists("user", "user-borrado")).thenReturn(false);

        // The row behind it is reported, which is what the sweep exists so as not to lose.
        assertThat(checker().reportOrphans()).isEqualTo(1);
    }

    @Test
    void aPassWithNoOrphansFindsNothing() {
        ApprovalRequest live = request("req-1", "user-1");
        when(approvalRequestRepository.findUnresolved(any(), any())).thenReturn(List.of(live));
        when(entityResolver.exists("user", "user-1")).thenReturn(true);

        assertThat(checker().reportOrphans()).isZero();
    }

    /**
     * The batch is bounded on purpose, like {@code FileRetentionService}'s: the work is idempotent and
     * read-only, so whatever is left over is picked up by the next pass.
     */
    @Test
    void tomaUnLoteAcotadoPorPasada() {
        when(approvalRequestRepository.findUnresolved(any(), any())).thenReturn(List.of());

        checker().reportOrphans();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(approvalRequestRepository).findUnresolved(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isPositive();
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    /** The method the scheduler calls is the same work, so the cron is not untested code. */
    @Test
    void theScheduledMethodRunsTheSamePass() {
        when(approvalRequestRepository.findUnresolved(any(), any())).thenReturn(List.of());

        checker().checkForOrphanedReferences();

        verify(approvalRequestRepository).findUnresolved(eq(ApprovalStatus.Pending), any(Pageable.class));
    }

    private static ApprovalRequest request(String id, String entityId) {
        User requester = new User("discord-" + id, "carla");
        ReflectionTestUtils.setField(requester, "id", "user-1");
        ApprovalRequest request = new ApprovalRequest(
                ApprovalRequestType.MasterGrant, ApprovalEntityResolver.USER, entityId, requester, "porque sí");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "createdAt", LocalDateTime.now());
        return request;
    }
}
