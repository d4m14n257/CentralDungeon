package com.centraldungeon.adminqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.approvals.ApprovalEntityResolver;
import com.centraldungeon.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

/**
 * The {@code {type}} segment of the two reservation routes, and the one thing it must not do: invent
 * a vocabulary.
 *
 * <p>The spelling is the one {@code approval_requests.entity_type} already uses (#78) and that
 * {@code Notification.relatedEntityType} publishes. A second vocabulary for the same things is how a
 * frontend ends up with two maps saying the same thing and a bug living between them - which is why
 * the shape is pinned here and not left to a reader to notice.
 */
class AdminQueueSourceTest {

    /**
     * Singular of the table, snake case - the same shape {@code ApprovalEntityResolver.USER} follows
     * for {@code "user"}, and the same {@code "game_table"} the notifications already publish.
     */
    @Test
    void hablaElMismoVocabularioQueEntityType() {
        assertThat(AdminQueueSource.APPROVAL_REQUEST.wireName()).isEqualTo("approval_request");
        assertThat(AdminQueueSource.GAME_TABLE.wireName()).isEqualTo("game_table");
        assertThat(ApprovalEntityResolver.USER).isEqualTo("user");
    }

    @Test
    void resuelveElSegmentoEnCualquierCaso() {
        assertThat(AdminQueueSource.require("game_table")).isEqualTo(AdminQueueSource.GAME_TABLE);
        assertThat(AdminQueueSource.require("  APPROVAL_REQUEST  ")).isEqualTo(AdminQueueSource.APPROVAL_REQUEST);
    }

    /**
     * <b>404 y no 400</b>: el segmento es parte del path, no de un body.
     * {@code /admin-queue/cualquiera/abc/claim} nombra un recurso que no existe, que es la misma
     * respuesta que recibe cualquier otro id que no está. Un 400 sería decirle a quien llama que su
     * request está mal formado cuando lo que pidió simplemente no existe.
     */
    @Test
    void unTipoQueNoExisteEs404() {
        assertThatThrownBy(() -> AdminQueueSource.require("comment"))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("NOT_FOUND");
    }

    /**
     * Dos fuentes vivas y no cuatro: {@code comments} y {@code system_feedback} llegan en F5 con la
     * feature que las produce (modelo-datos.md §5).
     */
    @Test
    void entranDosFuentesYNoCuatro() {
        assertThat(AdminQueueSource.values())
                .containsExactly(AdminQueueSource.APPROVAL_REQUEST, AdminQueueSource.GAME_TABLE);
        assertThat(AdminQueueSource.fromWireName("comment")).isEmpty();
        assertThat(AdminQueueSource.fromWireName("system_feedback")).isEmpty();
    }
}
