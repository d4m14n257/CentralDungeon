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
import com.centraldungeon.settings.SettingKey;
import com.centraldungeon.settings.SettingsService;
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

    @Mock
    private SettingsService settingsService;

    private AdminQueueClaimReleaseService service(Duration timeout) {
        when(settingsService.claimTimeout()).thenReturn(timeout);
        return new AdminQueueClaimReleaseService(approvalRequestRepository, gameTableRepository, settingsService);
    }

    /** What the job does, in one sentence: what was claimed becomes free, in both sources. */
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
     * is what the query is given: it is the difference between a value that can be adjusted without a
     * constante escondida. F3.5 lo muda a {@code system_settings} (#141) y este test no cambia.
     */
    @Test
    void theCutoffComesFromTheConfiguredTimeout() {
        expired(List.of(), List.of());
        LocalDateTime before = LocalDateTime.now();

        service(Duration.ofMinutes(30)).releaseExpiredClaims();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(approvalRequestRepository).findExpiredClaims(any(), cutoff.capture(), any());
        assertThat(cutoff.getValue()).isBefore(before.minusMinutes(29));
        assertThat(cutoff.getValue()).isAfter(before.minusMinutes(31));
    }

    /**
     * And with nothing configured, the value #100 says it starts at: fifteen minutes.
     *
     * <p>The default moved with the setting in F3.5 - it is {@code SettingKey}'s now, not a
     * {@code @ConfigurationProperties} record's - so the assertion moved to where the default lives.
     * {@code SettingsServiceTest} is what proves an unset setting answers with it; this one keeps the
     * number written down beside the job that uses it, because fifteen minutes is #100's number and
     * not an implementation detail of the settings table.
     */
    @Test
    void sinConfiguracionElTimeoutArrancaEnQuinceMinutos() {
        assertThat(Duration.ofMinutes(SettingKey.ADMIN_QUEUE_CLAIM_TIMEOUT_MINUTES.defaultValue()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    /**
     * <b>Only what is still waiting on somebody.</b> A request already answered, or a table already approved,
     * keeps its {@code claimed_by}: it is in nobody's tray, so clearing it would be a write with no reader
     * that also erases who was working on it.
     */
    @Test
    void itOnlyLooksAtWhatIsStillWaiting() {
        expired(List.of(), List.of());

        service(Duration.ofMinutes(15)).releaseExpiredClaims();

        verify(approvalRequestRepository).findExpiredClaims(eq(ApprovalStatus.Pending), any(), any());
        verify(gameTableRepository).findExpiredClaims(eq(GameTableStatus.Preparation), any(), any());
    }

    /** Bounded like the other two jobs: whatever is left is picked up by the next pass, sixty seconds later. */
    @Test
    void everyPassIsBounded() {
        expired(List.of(), List.of());

        service(Duration.ofMinutes(15)).releaseExpiredClaims();

        ArgumentCaptor<Pageable> batch = ArgumentCaptor.forClass(Pageable.class);
        verify(gameTableRepository).findExpiredClaims(any(), any(), batch.capture());
        assertThat(batch.getValue().getPageSize()).isEqualTo(200);
        assertThat(batch.getValue().getPageNumber()).isZero();
    }

    /** Zero is the ordinary answer on a platform where people finish what they start. */
    @Test
    void sinReservasVencidasNoDevuelveNada() {
        expired(List.of(), List.of());

        assertThat(service(Duration.ofMinutes(15)).releaseExpiredClaims()).isZero();
    }

    /**
     * The door the scheduler will call runs the same pass. There is no assertion beyond «it completes and
     * returns the same»: what is being guarded is that the entry point does not drift away from the work,
     * which is exactly what happens when somebody moves the logic and leaves the {@code @Scheduled}
     * llamando a otra cosa.
     */
    @Test
    void theSchedulersEntryPointRunsTheSamePass() {
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
