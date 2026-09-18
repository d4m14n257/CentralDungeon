package com.centraldungeon.adminqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.adminqueue.dto.AdminQueueItemResponse;
import com.centraldungeon.approvals.ApprovalRequest;
import com.centraldungeon.approvals.ApprovalRequestRepository;
import com.centraldungeon.approvals.ApprovalRequestType;
import com.centraldungeon.approvals.ApprovalStatus;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.MasterType;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Every rule of the shared tray (contrato F3.3 §1 y §2), one test each.
 *
 * <p>Three of them are what the slice is actually about and the rest exists so they cannot be broken
 * quietly:
 *
 * <ul>
 *   <li><b>What enters is taxative</b> (modelo-datos.md §5): {@code Pending} requests and tables in
 *       {@code Preparation}, <em>and nothing else</em>. A loose reading of #176 would also list
 *       {@code Draft}, {@code ChangesRequested} and {@code Unassigned}, and each of those is work that
 *       is not an admin's to do right now.</li>
 *   <li><b>A reserved item disappears for the rest and stays for whoever took it</b> - the filter the
 *       whole mechanism of #100 rests on, asserted as the actor reaching the query.</li>
 *   <li><b>Reserving is idempotent for the same admin and a 409 for anybody else.</b> Including the
 *       part that is easy to get wrong: the second click must not push {@code claimed_at} forward, or
 *       an admin clicking every fourteen minutes would hold an item for ever.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminQueueServiceTest {

    private static final String ACTOR = "admin-1";

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private GameTableRepository gameTableRepository;

    @Mock
    private MasterService masterService;

    @Mock
    private UserService userService;

    private AdminQueueService service() {
        return new AdminQueueService(approvalRequestRepository, gameTableRepository, masterService, userService);
    }

    // ------------------------------------------------------------------ what enters

    /**
     * «La bandeja es un UNION sobre {@code approval_requests} (Pending) y {@code game_tables}
     * (Preparation)» - y nada más (modelo-datos.md §5, #245).
     *
     * <p>Se afirma sobre los argumentos que llegan a cada consulta y no sobre lo que vuelve, porque lo
     * que hay que fijar es <em>qué se pregunta</em>. Un test que armara filas de mentira y contara las
     * de salida seguiría en verde el día que el filtro se ensanchara.
     */
    @Test
    void soloEntranPedidosPendientesYMesasEnPreparation() {
        noQueue();

        service().list(ACTOR, FIRST_PAGE);

        verify(approvalRequestRepository).findQueueItems(eq(ApprovalStatus.Pending), eq(ACTOR), any());
        verify(gameTableRepository).findQueueItems(eq(GameTableStatus.Preparation), eq(ACTOR), any());
        // Draft, ChangesRequested y Unassigned no entran: nadie las envió, la pelota está en el master,
        // o les falta un master y no una revisión.
        verify(gameTableRepository, never()).findQueueItems(eq(GameTableStatus.Draft), anyString(), any());
        verify(gameTableRepository, never()).findQueueItems(eq(GameTableStatus.ChangesRequested), anyString(), any());
        verify(gameTableRepository, never()).findQueueItems(eq(GameTableStatus.Unassigned), anyString(), any());
    }

    /**
     * El actor entra en el WHERE de las dos fuentes (#121, #100): un ítem que tomó otro admin no está
     * en esta respuesta, y uno que tomó este sí.
     */
    @Test
    void elActorEntraEnLaConsultaDeCadaFuente() {
        noQueue();

        service().list("admin-7", FIRST_PAGE);

        verify(approvalRequestRepository).findQueueItems(any(), eq("admin-7"), any());
        verify(gameTableRepository).findQueueItems(any(), eq("admin-7"), any());
    }

    /**
     * El techo por fuente del contrato §1.a. Un merge sin techo es una carga de memoria que nadie
     * declaró, y «no puede crecer» es la frase bajo la que se escribió toda lectura sin límite.
     */
    @Test
    void cadaFuenteTraeComoMuchoDoscientasFilas() {
        noQueue();

        service().list(ACTOR, FIRST_PAGE);

        ArgumentCaptor<Pageable> ceiling = ArgumentCaptor.forClass(Pageable.class);
        verify(approvalRequestRepository).findQueueItems(any(), anyString(), ceiling.capture());
        assertThat(ceiling.getValue().getPageSize()).isEqualTo(AdminQueueService.QUEUE_SOURCE_CAP);
        assertThat(ceiling.getValue().getPageNumber()).isZero();
    }

    /**
     * Y cuando lo toca, <b>lo dice, con el nombre de la fuente</b>. La mitad que importa: un techo que
     * se alcanza en silencio es una bandeja que sub-reporta sin que nadie se entere, que es la forma
     * exacta en que este límite se volvería un bug en vez de una protección.
     */
    @Test
    void siUnaFuenteTocaElTechoLoLogueaConSuNombre() {
        List<ApprovalRequest> lleno = new java.util.ArrayList<>();
        for (int i = 0; i < AdminQueueService.QUEUE_SOURCE_CAP; i++) {
            lleno.add(pendingRequest(String.format("req-%03d", i), "2026-09-01T09:00"));
        }
        queue(lleno, List.of());

        List<String> warnings = capturingWarnings(() -> service().list(ACTOR, FIRST_PAGE));

        assertThat(warnings).anySatisfy(line -> assertThat(line).contains("approval_request"));
    }

    // ------------------------------------------------------------------ el orden y la página

    /**
     * «El que espera hace más tiempo va primero», igual que la bandeja del master (#136): la urgencia
     * acá es tiempo y no volumen. Y el orden es <b>entre fuentes</b>, que es lo que hace que esto sea
     * una bandeja y no dos listados pegados.
     */
    @Test
    void elQueEsperaHaceMasTiempoVaPrimero() {
        ApprovalRequest viejo = pendingRequest("req-viejo", "2026-09-01T09:00");
        ApprovalRequest nuevo = pendingRequest("req-nuevo", "2026-09-03T09:00");
        GameTable mesa = tableInReview("table-1", "2026-09-02T09:00");
        queue(List.of(nuevo, viejo), List.of(mesa));

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, FIRST_PAGE);

        assertThat(page.content())
                .extracting(AdminQueueItemResponse::id)
                .containsExactly("req-viejo", "table-1", "req-nuevo");
    }

    /** Desempate por id, para que la página 2 sea el resto y no una baraja nueva (#171). */
    @Test
    void elEmpateSeRompePorIdParaQueLaPaginaDosSeaEstable() {
        ApprovalRequest primero = pendingRequest("req-aaa", "2026-09-01T09:00");
        ApprovalRequest segundo = pendingRequest("req-bbb", "2026-09-01T09:00");
        queue(List.of(segundo, primero), List.of());

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, FIRST_PAGE);

        assertThat(page.content()).extracting(AdminQueueItemResponse::id).containsExactly("req-aaa", "req-bbb");
    }

    /**
     * El total es el de la bandeja entera y no el del recorte: el merge ya está en memoria, así que
     * contarlo exacto sale gratis (#173).
     */
    @Test
    void elTotalEsLaBandejaEnteraYLaPaginaEsElRecorte() {
        queue(
                List.of(
                        pendingRequest("req-1", "2026-09-01T09:00"),
                        pendingRequest("req-2", "2026-09-02T09:00"),
                        pendingRequest("req-3", "2026-09-03T09:00")),
                List.of());

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, PageRequest.of(1, 2));

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.content()).extracting(AdminQueueItemResponse::id).containsExactly("req-3");
    }

    /** Una página más allá del final es vacía, no una excepción: el frontend puede pedirla desde la URL (#185). */
    @Test
    void unaPaginaMasAllaDelFinalEsVacia() {
        queue(List.of(pendingRequest("req-1", "2026-09-01T09:00")), List.of());

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, PageRequest.of(5, 20));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    /** La bandeja vacía es una buena noticia y no una pantalla rota (#136). */
    @Test
    void laBandejaVaciaEsUnaRespuestaNormal() {
        noQueue();

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, FIRST_PAGE);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    // ------------------------------------------------------------------ la forma del ítem

    @Test
    void unPedidoSePublicaConSuTipoSuJustificacionYQuienLoPidio() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        queue(List.of(request), List.of());

        AdminQueueItemResponse item = service().list(ACTOR, FIRST_PAGE).content().getFirst();

        assertThat(item.type()).isEqualTo("approval_request");
        assertThat(item.kind()).isEqualTo("ApprovalRequest");
        assertThat(item.title()).isEqualTo("MasterGrant");
        assertThat(item.detail()).isEqualTo("quiero dirigir");
        assertThat(item.requestedByName()).isEqualTo("carla");
        assertThat(item.waitingSince()).isEqualTo(LocalDateTime.parse("2026-09-01T09:00"));
        assertThat(item.claimedByName()).isNull();
    }

    /**
     * La mesa se publica con su nombre y con el master que la mandó, resuelto por lote. <b>Sin
     * detalle</b>, y no es un olvido: la justificación de una mesa es la mesa, y se lee abriéndola.
     */
    @Test
    void unaMesaSePublicaConSuNombreYSuMasterYSinDetalle() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        User primary = user("master-1", "ana");
        queue(List.of(), List.of(table));
        when(masterService.findPrimariesByTables(List.of("table-1")))
                .thenReturn(Map.of("table-1", new Master(table, primary, MasterType.Primary)));

        AdminQueueItemResponse item = service().list(ACTOR, FIRST_PAGE).content().getFirst();

        assertThat(item.type()).isEqualTo("game_table");
        assertThat(item.kind()).isEqualTo("TableWaitingReview");
        assertThat(item.title()).isEqualTo("La Cripta");
        assertThat(item.requestedByName()).isEqualTo("ana");
        assertThat(item.detail()).isNull();
    }

    /**
     * Y si la mesa no tiene Primary vivo, cae en quien creó la fila: sigue siendo cierto, y la bandeja
     * nunca imprime un id (#136).
     */
    @Test
    void unaMesaSinPrimaryVivoNombraAQuienLaCreo() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        queue(List.of(), List.of(table));
        when(masterService.findPrimariesByTables(List.of("table-1"))).thenReturn(Map.of());

        AdminQueueItemResponse item = service().list(ACTOR, FIRST_PAGE).content().getFirst();

        assertThat(item.requestedByName()).isEqualTo("quien-la-creo");
    }

    /** Los masters de toda la página se resuelven en una consulta, no en una por fila (#136, contrato §3.4). */
    @Test
    void losMastersDeLaPaginaSeResuelvenEnUnaSolaConsulta() {
        queue(
                List.of(),
                List.of(tableInReview("table-1", "2026-09-01T09:00"), tableInReview("table-2", "2026-09-02T09:00")));
        when(masterService.findPrimariesByTables(any())).thenReturn(Map.of());

        service().list(ACTOR, FIRST_PAGE);

        verify(masterService).findPrimariesByTables(List.of("table-1", "table-2"));
    }

    // ------------------------------------------------------------------ reservar

    @Test
    void reservarUnItemLibreLoDejaANombreDelAdmin() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        User admin = user(ACTOR, "damian");
        when(userService.getById(ACTOR)).thenReturn(admin);
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        AdminQueueItemResponse item = service().claim("approval_request", "req-1", ACTOR);

        assertThat(request.getClaimedBy()).isEqualTo(admin);
        assertThat(request.getClaimedAt()).isNotNull();
        assertThat(item.claimedByName()).isEqualTo("damian");
    }

    /**
     * Idempotente para el mismo admin, y <b>sin mover {@code claimed_at}</b>. Lo segundo es la mitad
     * que se olvida: si cada clic refrescara el reloj, un admin clickeando cada catorce minutos se
     * quedaría con el ítem para siempre y el job nunca lo alcanzaría.
     */
    @Test
    void reservarDosVecesElMismoAdminNoMueveElReloj() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        User admin = user(ACTOR, "damian");
        LocalDateTime original = LocalDateTime.parse("2026-09-01T10:00");
        request.claim(admin, original);
        when(userService.getById(ACTOR)).thenReturn(admin);
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        AdminQueueItemResponse item = service().claim("approval_request", "req-1", ACTOR);

        assertThat(request.getClaimedAt()).isEqualTo(original);
        assertThat(item.claimedByName()).isEqualTo("damian");
    }

    /** Si lo tiene otro, 409 con el código que la pantalla necesita para escribir la frase (#197). */
    @Test
    void reservarAlgoQueTieneOtroAdminEs409() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        request.claim(user("admin-2", "otra"), LocalDateTime.parse("2026-09-01T10:00"));
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().claim("approval_request", "req-1", ACTOR))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);

        assertThat(request.getClaimedBy().getId()).isEqualTo("admin-2");
    }

    /** Se reserva bajo el lock de la fila, que es lo que hace que la carrera de dos admins tenga un ganador (#252, #256). */
    @Test
    void laReservaSeTomaBajoElLockDeLaFila() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        service().claim("approval_request", "req-1", ACTOR);

        verify(approvalRequestRepository).lockById("req-1");
        verify(approvalRequestRepository, never()).findById(anyString());
    }

    /** Lo mismo del lado de las mesas: {@code findByIdForUpdate} y no {@code findById}. */
    @Test
    void reservarUnaMesaTambienPasaPorElLock() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));
        when(masterService.findPrimariesByTables(List.of("table-1"))).thenReturn(Map.of());

        service().claim("game_table", "table-1", ACTOR);

        assertThat(table.getClaimedBy().getId()).isEqualTo(ACTOR);
        verify(gameTableRepository).findByIdForUpdate("table-1");
    }

    /** Un ítem que ya no espera a nadie no se reserva: no está en la bandeja de nadie. */
    @Test
    void noSeReservaUnPedidoQueYaFueResuelto() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        request.resolve(ApprovalStatus.Approved, user("admin-2", "otra"), "listo");
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().claim("approval_request", "req-1", ACTOR))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void noSeReservaUnaMesaQueYaNoEstaEnRevision() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        table.setStatus(GameTableStatus.Opened);
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> service().claim("game_table", "table-1", ACTOR))
                .isInstanceOf(ConflictException.class);
    }

    /** Una mesa borrada no existe acá tampoco (#25, #175): 404 y no 403 - «fue borrada» no se cuenta. */
    @Test
    void unaMesaBorradaNoExisteParaLaBandeja() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        table.setStatus(GameTableStatus.Deleted);
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> service().claim("game_table", "table-1", ACTOR))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reservarUnTipoQueNoExisteEs404() {
        assertThatThrownBy(() -> service().claim("comment", "c-1", ACTOR)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void reservarAlgoQueNoExisteEs404() {
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(approvalRequestRepository.lockById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().claim("approval_request", "nope", ACTOR))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------ devolver

    @Test
    void devolverLoPropioLoDejaLibre() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        request.claim(user(ACTOR, "damian"), LocalDateTime.parse("2026-09-01T10:00"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        service().release("approval_request", "req-1", ACTOR);

        assertThat(request.getClaimedBy()).isNull();
        assertThat(request.getClaimedAt()).isNull();
    }

    /** Devolver algo que nadie tiene es idempotente: el estado que se pidió ya se cumple. */
    @Test
    void devolverAlgoSinReservaNoEsUnError() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        service().release("approval_request", "req-1", ACTOR);

        assertThat(request.getClaimedBy()).isNull();
    }

    /** Devolver lo de otro no: es la única forma en que un admin le sacaría el trabajo de la mesa a otro. */
    @Test
    void devolverLoDeOtroEs409() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        User otra = user("admin-2", "otra");
        request.claim(otra, LocalDateTime.parse("2026-09-01T10:00"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().release("approval_request", "req-1", ACTOR))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);

        assertThat(request.getClaimedBy()).isEqualTo(otra);
    }

    /**
     * Devolver no pregunta en qué estado está el ítem, a diferencia de reservar: devolver es el
     * deshacer, y un deshacer que puede negarse deja a alguien trabado.
     */
    @Test
    void devolverNoLePreguntaAlEstadoDelItem() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        table.claim(user(ACTOR, "damian"), LocalDateTime.parse("2026-09-01T10:00"));
        table.setStatus(GameTableStatus.Opened);
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));

        service().release("game_table", "table-1", ACTOR);

        assertThat(table.getClaimedBy()).isNull();
    }

    // ------------------------------------------------------------------ armado

    private void noQueue() {
        queue(List.of(), List.of());
    }

    /**
     * Runs something with the service's own logger tapped, and hands back the WARN lines it wrote.
     *
     * <p>The only assertion in this file that looks at a log rather than at a return value, and it is
     * the right shape for this one rule: the ceiling's whole job is to be <em>noticed</em>, so what has
     * to be true is that a line comes out naming the source. Anything else - a counter, a flag - would
     * be testing a mechanism invented for the test.
     */
    private static List<String> capturingWarnings(Runnable call) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AdminQueueService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            call.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return appender.list.stream()
                .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private void queue(List<ApprovalRequest> requests, List<GameTable> tables) {
        when(approvalRequestRepository.findQueueItems(any(), anyString(), any())).thenReturn(requests);
        when(gameTableRepository.findQueueItems(any(), anyString(), any())).thenReturn(tables);
        when(masterService.findPrimariesByTables(any())).thenReturn(Map.of());
    }

    private static ApprovalRequest pendingRequest(String id, String createdAt) {
        ApprovalRequest request = new ApprovalRequest(
                ApprovalRequestType.MasterGrant, "user", "user-1", user("user-1", "carla"), "quiero dirigir");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "createdAt", LocalDateTime.parse(createdAt));
        return request;
    }

    private static GameTable tableInReview(String id, String createdAt) {
        GameTable table = new GameTable("La Cripta", user("creator-1", "quien-la-creo"));
        ReflectionTestUtils.setField(table, "id", id);
        ReflectionTestUtils.setField(table, "createdAt", LocalDateTime.parse(createdAt));
        table.setStatus(GameTableStatus.Preparation);
        return table;
    }

    private static User user(String id, String discordUsername) {
        User user = new User("discord-" + id, discordUsername);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
