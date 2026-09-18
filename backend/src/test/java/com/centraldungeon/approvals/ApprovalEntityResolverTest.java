package com.centraldungeon.approvals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.centraldungeon.users.UserRepository;
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

    private ApprovalEntityResolver resolver() {
        return new ApprovalEntityResolver(userRepository);
    }

    @Test
    void unUsuarioQueExisteResuelve() {
        when(userRepository.existsById("user-1")).thenReturn(true);

        assertThat(resolver().exists(ApprovalEntityResolver.USER, "user-1")).isTrue();
    }

    @Test
    void unUsuarioBorradoNoResuelve() {
        when(userRepository.existsById("user-borrado")).thenReturn(false);

        assertThat(resolver().exists(ApprovalEntityResolver.USER, "user-borrado")).isFalse();
    }

    /**
     * Un tipo sin caso es un error de programación y se nombra como tal. F3.4 agrega
     * {@code game_table} y {@code table_registration} cuando agregue los flujos que los emiten; si se
     * olvida del caso, este es el mensaje que va a leer.
     */
    @Test
    void unTipoDeEntidadDesconocidoFallaDiciendoQueEsDesconocido() {
        assertThatThrownBy(() -> resolver().exists("game_table", "mesa-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown approval entity type")
                .hasMessageContaining("game_table");
    }
}
