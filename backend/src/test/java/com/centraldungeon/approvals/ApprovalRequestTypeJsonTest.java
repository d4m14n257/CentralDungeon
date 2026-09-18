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
     * Los dos tipos que F3.4 trae con su productor <b>no existen todavía</b>, y eso es una decisión,
     * no un olvido (contrato F3.2, 0.a): un valor de enum que nada produce es el huérfano que esta
     * fase vino a cerrar. Si alguien los agrega, que sea con el flujo que los emite - y este test se
     * cae primero.
     */
    @Test
    void entranTresTiposYNoCinco() {
        assertThat(ApprovalRequestType.values())
                .containsExactly(
                        ApprovalRequestType.MasterGrant, ApprovalRequestType.TableOpen, ApprovalRequestType.General);

        assertThatThrownBy(() -> json.readValue("\"TablePause\"", ApprovalRequestType.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> json.readValue("\"PlayerBan\"", ApprovalRequestType.class))
                .isInstanceOf(Exception.class);
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
     * Cada tipo declara de qué entidad habla, y en F3.2 los tres hablan de quien pide. Es lo que
     * mantiene la invariante de #78 sin una columna nullable por flujo (#126) - y lo que F3.4 cambia
     * al agregar un pedido sobre una mesa.
     */
    @Test
    void cadaTipoDeclaraSuTipoDeEntidad() {
        for (ApprovalRequestType type : ApprovalRequestType.values()) {
            assertThat(type.entityType()).isEqualTo(ApprovalEntityResolver.USER);
        }
    }
}
