package com.centraldungeon.approvals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.approvals.dto.SubmitApprovalRequestRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * How {@link ApprovalRequestType} crosses HTTP, in both directions.
 *
 * <p>This test exists because of #253, which F3.1 paid for once already. {@code PlatformRole} crossed
 * HTTP without {@code @JsonValue}: every response published {@code "Player"} - built by hand in a
 * mapper - and every request carrying that same value was rejected with a 400. No service test could
 * see it, because a service test calls the method with the enum already in hand, and the frontend's
 * tests mock the fetch. The only thing that catches it is asking the mapper, which is what this does.
 *
 * <p>The constants here already coincide with their wire names, so Jackson's default would happen to
 * produce the same strings today. That is precisely why the pin is worth having: it is the day
 * somebody renames a constant, or adds a type whose wire name differs, that the two would drift - and
 * the drift is silent in exactly one direction.
 *
 * <p>Jackson 3 ({@code tools.jackson.*}), like the rest of the application.
 */
class ApprovalRequestTypeJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void seSerializaConElNombreDeWireYNoConLaConstante() {
        assertThat(json.writeValueAsString(ApprovalRequestType.MasterGrant)).isEqualTo("\"MasterGrant\"");
        assertThat(json.writeValueAsString(ApprovalRequestType.TableOpen)).isEqualTo("\"TableOpen\"");
        assertThat(json.writeValueAsString(ApprovalRequestType.General)).isEqualTo("\"General\"");
    }

    /** Lo que la API publica es lo que la API acepta - si no, el frontend no puede devolver lo que leyó. */
    @Test
    void seDeserializaElMismoValorQuePublicaLaRespuesta() {
        for (ApprovalRequestType type : ApprovalRequestType.values()) {
            String published = json.writeValueAsString(type).replace("\"", "");

            assertThat(json.readValue("\"" + published + "\"", ApprovalRequestType.class)).isEqualTo(type);
        }
    }

    @Test
    void elCuerpoDeUnPedidoEntraConElTipoTalCualLoMandaElFrontend() {
        SubmitApprovalRequestRequest request = json.readValue(
                "{\"type\":\"MasterGrant\",\"justification\":\"quiero dirigir\"}",
                SubmitApprovalRequestRequest.class);

        assertThat(request.type()).isEqualTo(ApprovalRequestType.MasterGrant);
        assertThat(request.justification()).isEqualTo("quiero dirigir");
    }

    /**
     * All five, <b>and each with its producer</b>. F3.2 declared three on purpose and this test used to
     * say «tres y no cinco» so that adding the other two without the flow that emits them would fail
     * here first - the orphan that phase came to close. F3.4 built both producers, so the assertion
     * inverts: all five exist, and the two new ones travel over the wire.
     */
    @Test
    void entranLosCincoTiposConSuProductor() {
        assertThat(ApprovalRequestType.values())
                .containsExactly(
                        ApprovalRequestType.MasterGrant, ApprovalRequestType.TableOpen, ApprovalRequestType.General,
                        ApprovalRequestType.TablePause, ApprovalRequestType.PlayerBan);

        assertThat(json.readValue("\"TablePause\"", ApprovalRequestType.class))
                .isEqualTo(ApprovalRequestType.TablePause);
        assertThat(json.readValue("\"PlayerBan\"", ApprovalRequestType.class))
                .isEqualTo(ApprovalRequestType.PlayerBan);
    }

    /**
     * Las dos puertas, una al lado de la otra: el body JSON y el texto del {@code ?q=} cuando alguien
     * escribe {@code /request_type MasterGrant}. Difieren a propósito en cuánto perdonan - un body es
     * exacto, una caja de búsqueda sobrevive a que la escriban - y la segunda falla más calladita: no
     * da 400, matchea cero filas.
     */
    @Test
    void elBodyEsExactoYLaCajaDeBusquedaPerdona() {
        assertThatThrownBy(() -> json.readValue("\"mastergrant\"", ApprovalRequestType.class))
                .isInstanceOf(Exception.class);

        assertThat(ApprovalRequestType.fromWireName("mastergrant")).contains(ApprovalRequestType.MasterGrant);
        assertThat(ApprovalRequestType.fromWireName("MASTERGRANT")).contains(ApprovalRequestType.MasterGrant);
        assertThat(ApprovalRequestType.fromWireName("Wizard")).isEmpty();
    }

    /**
     * Every type declares which kind of entity it is about. The three from F3.2 are about the person
     * asking, which is what keeps the invariant of #78 without a nullable column per flow (#126); the
     * two from F3.4 are the first that point at something else, and therefore the first that need a
     * case in {@link ApprovalEntityResolver}.
     */
    @Test
    void cadaTipoDeclaraSuTipoDeEntidad() {
        assertThat(ApprovalRequestType.MasterGrant.entityType()).isEqualTo(ApprovalEntityResolver.USER);
        assertThat(ApprovalRequestType.TableOpen.entityType()).isEqualTo(ApprovalEntityResolver.USER);
        assertThat(ApprovalRequestType.General.entityType()).isEqualTo(ApprovalEntityResolver.USER);
        assertThat(ApprovalRequestType.TablePause.entityType()).isEqualTo(ApprovalEntityResolver.GAME_TABLE);
        assertThat(ApprovalRequestType.PlayerBan.entityType()).isEqualTo(ApprovalEntityResolver.TABLE_REGISTRATION);
    }
}
