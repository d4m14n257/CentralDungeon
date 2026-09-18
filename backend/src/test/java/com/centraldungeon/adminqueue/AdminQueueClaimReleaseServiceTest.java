package com.centraldungeon.adminqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.approvals.ApprovalRequest;
import com.centraldungeon.approvals.ApprovalRequestRepository;
import com.centraldungeon.approvals.ApprovalRequestType;
import com.centraldungeon.approvals.ApprovalStatus;
import com.centraldungeon.common.config.AdminQueueProperties;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.users.User;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The job that hands back the reservations nobody finished (#100).
 *
 * <p>Asserted through {@link AdminQueueClaimReleaseService#releaseExpiredClaims()} and not through the
 * scheduled method, for the reason {@code FileRetentionServiceTest} and
 * {@code ApprovalOrphanCheckServiceTest} give: a test should not have to wait sixty seconds for a
 * {@code fixedDelay} to come round, and the rule worth testing is <em>which</em> reservations expire,
 * not that Spring can read an annotation. That the real work lives in a public method of its own is
 * exactly what makes this possible - and it is also what the self-invocation trap forces, since the
 * scheduled entry point has to carry {@code @Transactional} of its own.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminQueueClaimReleaseServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private GameTableRepository gameTableRepository;

    private AdminQueueClaimReleaseService service(Duration timeout) {
        return new AdminQueueClaimReleaseService(
                approvalRequestRepository, gameTableRepository, new AdminQueueProperties(timeout));
    }

    /** Lo que el job hace, en una frase: lo que estaba reservado queda libre, en las dos fuentes. */
    @Test
    void devuelveLasReservasVencidasDeLasDosFuentes() {
        ApprovalRequest request = claimedRequest();
        GameTable table = claimedTable();
        expired(List.of(request), List.of(table));

        int released = service(Duration.ofMinutes(15)).releaseExpiredClaims();

        assertThat(released).isEqualTo(2);
        assertThat(request.getClaimedBy()).isNull();
        assertThat(request.getClaimedAt()).isNull();
        assertThat(table.getClaimedBy()).isNull();
        assertThat(table.getClaimedAt()).isNull();
    }

    /**
     * El corte sale del timeout configurable (contrato §2, {@code app.admin-queue.claim-timeout}), y
     * se le pasa a la consulta: es la diferencia entre un valor que se puede ajustar sin deploy y una
     * constante escondida. F3.5 lo muda a {@code system_settings} (#141) y este test no cambia.
     */
    @Test
    void elCorteSaleDelTimeoutConfigurado() {
        expired(List.of(), List.of());
        LocalDateTime before = LocalDateTime.now();

        service(Duration.ofMinutes(30)).releaseExpiredClaims();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(approvalRequestRepository).findExpiredClaims(any(), cutoff.capture(), any());
        assertThat(cutoff.getValue()).isBefore(before.minusMinutes(29));
        assertThat(cutoff.getValue()).isAfter(before.minusMinutes(31));
    }

    /** Y sin configuración, el valor con el que #100 dice que arranca: quince minutos. */
    @Test
    void sinConfiguracionElTimeoutArrancaEnQuinceMinutos() {
        assertThat(new AdminQueueProperties(null).claimTimeout()).isEqualTo(Duration.ofMinutes(15));
    }

    /**
     * <b>Solo lo que sigue esperando a alguien.</b> Un pedido ya respondido, o una mesa ya aprobada,
     * conserva su {@code claimed_by}: no está en la bandeja de nadie, así que limpiarlo sería una
     * escritura sin lector que además borra quién lo estaba trabajando.
     */
    @Test
    void soloMiraLoQueTodaviaEstaEsperando() {
        expired(List.of(), List.of());

        service(Duration.ofMinutes(15)).releaseExpiredClaims();

        verify(approvalRequestRepository).findExpiredClaims(eq(ApprovalStatus.Pending), any(), any());
        verify(gameTableRepository).findExpiredClaims(eq(GameTableStatus.Preparation), any(), any());
    }

    /** Acotado como los otros dos jobs: lo que quede lo levanta la pasada siguiente, sesenta segundos después. */
    @Test
    void cadaPasadaEstaAcotada() {
        expired(List.of(), List.of());

        service(Duration.ofMinutes(15)).releaseExpiredClaims();

        ArgumentCaptor<Pageable> batch = ArgumentCaptor.forClass(Pageable.class);
        verify(gameTableRepository).findExpiredClaims(any(), any(), batch.capture());
        assertThat(batch.getValue().getPageSize()).isEqualTo(200);
        assertThat(batch.getValue().getPageNumber()).isZero();
    }

    /** Cero es la respuesta normal de una plataforma donde la gente termina lo que empieza. */
    @Test
    void sinReservasVencidasNoDevuelveNada() {
        expired(List.of(), List.of());

        assertThat(service(Duration.ofMinutes(15)).releaseExpiredClaims()).isZero();
    }

    /**
     * La puerta que va a llamar el scheduler corre la misma pasada. No hay más aserción que «termina
     * y devuelve lo mismo»: lo que se está cuidando es que el punto de entrada no se separe del
     * trabajo, que es justo lo que pasa cuando alguien mueve la lógica y deja el {@code @Scheduled}
     * llamando a otra cosa.
     */
    @Test
    void elPuntoDeEntradaDelSchedulerCorreLaMismaPasada() {
        ApprovalRequest request = claimedRequest();
        expired(List.of(request), List.of());

        service(Duration.ofMinutes(15)).releaseTimedOutClaims();

        assertThat(request.getClaimedBy()).isNull();
    }

    // ------------------------------------------------------------------ armado

    private void expired(List<ApprovalRequest> requests, List<GameTable> tables) {
        when(approvalRequestRepository.findExpiredClaims(any(), any(), any())).thenReturn(requests);
        when(gameTableRepository.findExpiredClaims(any(), any(), any())).thenReturn(tables);
    }

    private static ApprovalRequest claimedRequest() {
        ApprovalRequest request = new ApprovalRequest(
                ApprovalRequestType.General, "user", "user-1", user("user-1", "carla"), "una consulta");
        ReflectionTestUtils.setField(request, "id", "req-1");
        ReflectionTestUtils.setField(request, "createdAt", LocalDateTime.parse("2026-09-01T09:00"));
        request.claim(user("admin-1", "damian"), LocalDateTime.parse("2026-09-01T09:05"));
        return request;
    }

    private static GameTable claimedTable() {
        GameTable table = new GameTable("La Cripta", user("creator-1", "ana"));
        ReflectionTestUtils.setField(table, "id", "table-1");
        ReflectionTestUtils.setField(table, "createdAt", LocalDateTime.parse("2026-09-01T09:00"));
        table.setStatus(GameTableStatus.Preparation);
        table.claim(user("admin-1", "damian"), LocalDateTime.parse("2026-09-01T09:05"));
        return table;
    }

    private static User user(String id, String discordUsername) {
        User user = new User("discord-" + id, discordUsername);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
