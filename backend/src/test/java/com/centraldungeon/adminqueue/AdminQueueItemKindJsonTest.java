package com.centraldungeon.adminqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.adminqueue.dto.AdminQueueItemResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * How {@link AdminQueueItemKind} crosses HTTP, in both directions.
 *
 * <p>This test exists because of #253, which this phase has already paid for twice.
 * {@code PlatformRole} crossed HTTP without {@code @JsonValue}: every response published
 * {@code "Player"} - built by hand in a mapper - and every request carrying that same value was
 * rejected with a 400. No service test could see it, because a service test calls the method with the
 * enum already in hand, and the frontend's tests mock the fetch. The only thing that catches it is
 * asking the mapper, which is what this does.
 *
 * <p>The constants already coincide with their wire names, so Jackson's default would produce the
 * same strings today. That is precisely why the pin is worth having: it is the day somebody renames a
 * constant, or F5 adds a kind whose wire name differs, that the two would drift - and the drift is
 * silent in exactly one direction.
 *
 * <p>Jackson 3 ({@code tools.jackson.*}), like the rest of the application.
 */
class AdminQueueItemKindJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void seSerializaConElNombreDeWireYNoConLaConstante() {
        assertThat(json.writeValueAsString(AdminQueueItemKind.ApprovalRequest)).isEqualTo("\"ApprovalRequest\"");
        assertThat(json.writeValueAsString(AdminQueueItemKind.TableWaitingReview))
                .isEqualTo("\"TableWaitingReview\"");
    }

    /** Lo que la API publica es lo que la API acepta - si no, el frontend no puede devolver lo que leyó. */
    @Test
    void seDeserializaElMismoValorQuePublicaLaRespuesta() {
        for (AdminQueueItemKind kind : AdminQueueItemKind.values()) {
            String published = json.writeValueAsString(kind).replace("\"", "");

            assertThat(json.readValue("\"" + published + "\"", AdminQueueItemKind.class)).isEqualTo(kind);
        }
    }

    /**
     * El {@code kind} viaja como string dentro del ítem, y el string es el mismo que el enum publica.
     * Es la mitad que se rompe sin que nadie la vea: el DTO lo arma el service a mano, así que si el
     * enum cambiara de ortografía el listado seguiría compilando y el frontend dejaría de reconocerlo.
     */
    @Test
    void elItemPublicaElMismoStringQueElEnum() {
        AdminQueueItemResponse item = new AdminQueueItemResponse(
                AdminQueueSource.GAME_TABLE.wireName(),
                "table-1",
                AdminQueueItemKind.TableWaitingReview.wireName(),
                "La Cripta",
                "ana",
                null,
                LocalDateTime.parse("2026-09-01T10:00"),
                null,
                null);

        String serialized = json.writeValueAsString(item);

        assertThat(serialized).contains("\"kind\":\"TableWaitingReview\"");
        assertThat(serialized).contains("\"type\":\"game_table\"");
        assertThat(json.readValue("\"" + item.kind() + "\"", AdminQueueItemKind.class))
                .isEqualTo(AdminQueueItemKind.TableWaitingReview);
    }

    /**
     * <b>Dos valores y no cuatro</b>, y es una decisión y no un olvido. {@code modelo-datos.md} §5
     * enumera cuatro fuentes: las dos de acá, más {@code comments} en {@code Under review} y
     * {@code system_feedback} en {@code New} - y esas dos llegan en F5 <em>con la feature que las
     * produce</em>. Un valor de enum que nada emite es el huérfano que esta fase vino a cerrar.
     */
    @Test
    void entranDosClasesYNoCuatro() {
        assertThat(AdminQueueItemKind.values())
                .containsExactly(AdminQueueItemKind.ApprovalRequest, AdminQueueItemKind.TableWaitingReview);

        assertThatThrownBy(() -> json.readValue("\"CommentUnderReview\"", AdminQueueItemKind.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> json.readValue("\"SystemFeedback\"", AdminQueueItemKind.class))
                .isInstanceOf(Exception.class);
    }

    /**
     * Las dos puertas, una al lado de la otra: el body JSON es exacto y la búsqueda por nombre
     * perdona. Difieren a propósito, igual que en {@code ApprovalRequestType}.
     */
    @Test
    void elBodyEsExactoYLaBusquedaPorNombrePerdona() {
        assertThatThrownBy(() -> json.readValue("\"approvalrequest\"", AdminQueueItemKind.class))
                .isInstanceOf(Exception.class);

        assertThat(AdminQueueItemKind.fromWireName("approvalrequest"))
                .contains(AdminQueueItemKind.ApprovalRequest);
        assertThat(AdminQueueItemKind.fromWireName("  TABLEWAITINGREVIEW  "))
                .contains(AdminQueueItemKind.TableWaitingReview);
        assertThat(AdminQueueItemKind.fromWireName("Whatever")).isEmpty();
    }
}
