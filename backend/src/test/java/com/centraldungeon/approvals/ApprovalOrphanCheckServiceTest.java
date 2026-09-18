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
    void cuentaLosPedidosCuyaReferenciaYaNoResuelve() {
        ApprovalRequest live = request("req-1", "user-1");
        ApprovalRequest orphan = request("req-2", "user-borrado");
        when(approvalRequestRepository.findUnresolved(eq(ApprovalStatus.Pending), any())).thenReturn(List.of(live, orphan));
        when(entityResolver.exists("user", "user-1")).thenReturn(true);
        when(entityResolver.exists("user", "user-borrado")).thenReturn(false);

        assertThat(checker().reportOrphans()).isEqualTo(1);
    }

    /**
     * Solo las no resueltas. Si una aprobada de hace dos años apunta a algo borrado no hay nada que
     * hacer al respecto; una abierta que apunta a la nada es un pedido que un admin no va a poder
     * resolver — le responde {@code REQUEST_ENTITY_GONE}.
     */
    @Test
    void recorreSoloLosPedidosSinResolver() {
        when(approvalRequestRepository.findUnresolved(any(), any())).thenReturn(List.of());

        checker().reportOrphans();

        verify(approvalRequestRepository).findUnresolved(eq(ApprovalStatus.Pending), any(Pageable.class));
    }

    /**
     * No borra y no resuelve. Un borrado automático sobre datos que ya perdieron su ancla es
     * exactamente cómo se pierde la evidencia de qué apuntaba a qué (#25, #126).
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
     * Un tipo de entidad sin caso en el resolver es un bug del código, no una huérfana — y no puede
     * costar el reporte de las demás. La pasada sobrevive, no lo cuenta como huérfana (no lo es:
     * nadie sabe qué es) y sigue con las que quedan.
     */
    @Test
    void unTipoDesconocidoNoMataLaPasadaNiCuentaComoHuerfana() {
        ApprovalRequest desconocida = request("req-3", "mesa-1");
        ReflectionTestUtils.setField(desconocida, "entityType", "game_table");
        ApprovalRequest orphan = request("req-2", "user-borrado");
        when(approvalRequestRepository.findUnresolved(any(), any())).thenReturn(List.of(desconocida, orphan));
        when(entityResolver.exists("game_table", "mesa-1"))
                .thenThrow(new IllegalStateException("Unknown approval entity type: game_table"));
        when(entityResolver.exists("user", "user-borrado")).thenReturn(false);

        // La fila de atrás sí se reporta, que es lo que el barrido existe para no perder.
        assertThat(checker().reportOrphans()).isEqualTo(1);
    }

    @Test
    void unaPasadaSinHuerfanasNoEncuentraNada() {
        ApprovalRequest live = request("req-1", "user-1");
        when(approvalRequestRepository.findUnresolved(any(), any())).thenReturn(List.of(live));
        when(entityResolver.exists("user", "user-1")).thenReturn(true);

        assertThat(checker().reportOrphans()).isZero();
    }

    /**
     * El lote está acotado a propósito, igual que el de {@code FileRetentionService}: el trabajo es
     * idempotente y de solo lectura, así que lo que sobra lo levanta la pasada siguiente.
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

    /** El método que corre el scheduler es el mismo trabajo, para que el cron no sea código sin probar. */
    @Test
    void elMetodoAgendadoHaceLaMismaPasada() {
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
