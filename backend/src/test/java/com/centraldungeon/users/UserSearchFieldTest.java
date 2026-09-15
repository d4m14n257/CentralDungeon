package com.centraldungeon.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.search.SearchTerm;
import org.junit.jupiter.api.Test;

/**
 * F3.1 taught {@code UserSearchSpecification} to serve two audiences, and the risk it introduced is
 * that the picker learns what only the admin listing should know. These tests pin the line.
 *
 * <p>The other half of that line - that {@code GET /users/search} still refuses to return a blocked
 * account - is fixed by {@code UserSearchIT.neverOffersSomeoneWhoIsNotAllowed}, against real SQL: it
 * is a predicate, and only the database can say whether it survived.
 */
class UserSearchFieldTest {

    @Test
    void elBuscadorDelPickerSoloConoceLosDosNombres() {
        assertThat(UserSearchField.wireNames()).containsExactlyInAnyOrder("discord_name", "user_name");
    }

    @Test
    void elBuscadorDeAdminSumaRolYEstado() {
        assertThat(UserSearchField.adminWireNames())
                .containsExactlyInAnyOrder("discord_name", "user_name", "role", "status");
    }

    /**
     * El picker no puede ni siquiera <em>expresar</em> un filtro por estado: {@code /status} no está
     * en su vocabulario, así que el parser lo deja como texto literal (§2.5), nunca como criterio.
     */
    @Test
    void elPickerBuscaSlashStatusComoTextoLiteralYNoComoCriterio() {
        SearchQuery query = SearchQueryParser.parse("/status Blocked", UserSearchField.wireNames());

        assertThat(query.terms()).singleElement().satisfies(term -> {
            assertThat(term.field()).isNull();
            assertThat(term.values()).containsExactly("/status Blocked");
        });
    }

    @Test
    void elBuscadorDeAdminSiLoParseaComoCriterio() {
        SearchQuery query = SearchQueryParser.parse("/status Blocked", UserSearchField.adminWireNames());

        assertThat(query.terms()).singleElement().extracting(SearchTerm::field).isEqualTo("status");
    }

    @Test
    void elBuscadorDeAdminReconoceElComandoDeRol() {
        SearchQuery query = SearchQueryParser.parse("/role Owner", UserSearchField.adminWireNames());

        assertThat(query.terms()).singleElement().satisfies(term -> {
            assertThat(term.field()).isEqualTo("role");
            assertThat(term.values()).containsExactly("Owner");
        });
    }

    /** Un rol que no existe no es un 400: no matchea nada (§2.5). */
    @Test
    void unNombreDeRolDesconocidoNoResuelveANingunaConstante() {
        assertThat(PlatformRole.fromRoleName("Wizard")).isEmpty();
        assertThat(PlatformRole.fromRoleName("owner")).contains(PlatformRole.OWNER);
    }

    @Test
    void elOrdenDeLosChipsEsElDeLaDeclaracionDeLosRoles() {
        assertThat(PlatformRole.ordered(java.util.Set.of("Owner", "Player"))).containsExactly("Player", "Owner");
        assertThat(PlatformRole.ordered(java.util.List.of("Admin", "Master", "Player")))
                .containsExactly("Player", "Master", "Admin");
        assertThat(PlatformRole.ordered(java.util.Set.of("Wizard"))).isEmpty();
    }
}
