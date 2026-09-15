package com.centraldungeon.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.users.dto.GrantRoleRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * How {@link PlatformRole} crosses HTTP, in both directions.
 *
 * <p>This test exists because of a bug nothing else could have caught. {@code PlatformRole} is the
 * one enum in the application whose constants ({@code PLAYER}) are spelled differently from the
 * value on the wire ({@code "Player"}, mirroring {@code roles.name}). Jackson reads and writes
 * {@code Enum.name()} by default, and {@code ACCEPT_CASE_INSENSITIVE_ENUMS} is off - there is no
 * {@code spring.jackson.*} in any of the four {@code application*.yml}. So without
 * {@code @JsonValue} the API published {@code "Player"} in every response (built by hand in
 * {@code UserMapper}) and rejected {@code "Player"} in every request with a 400.
 *
 * <p>No service test could see it: a service test calls the method with a {@code PlatformRole}
 * already in hand, and the frontend's tests mock the fetch. The only thing that catches it is
 * asking the mapper, which is what this does.
 *
 * <p>Jackson 3 ({@code tools.jackson.*}), like the rest of the application.
 */
class PlatformRoleJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void seSerializaComoLoEscribeLaTablaRolesYNoComoLaConstante() {
        assertThat(json.writeValueAsString(PlatformRole.PLAYER)).isEqualTo("\"Player\"");
        assertThat(json.writeValueAsString(PlatformRole.MASTER)).isEqualTo("\"Master\"");
        assertThat(json.writeValueAsString(PlatformRole.ADMIN)).isEqualTo("\"Admin\"");
        assertThat(json.writeValueAsString(PlatformRole.OWNER)).isEqualTo("\"Owner\"");
    }

    /** Lo que la API publica es lo que la API acepta - si no, el frontend no puede devolver lo que leyó. */
    @Test
    void seDeserializaElMismoValorQuePublicaLaRespuesta() {
        assertThat(json.readValue("\"Player\"", PlatformRole.class)).isEqualTo(PlatformRole.PLAYER);
        assertThat(json.readValue("\"Owner\"", PlatformRole.class)).isEqualTo(PlatformRole.OWNER);
    }

    @Test
    void elCuerpoDeGrantRoleEntraConElNombreDelRolTalCualLoManaElFrontend() {
        GrantRoleRequest request =
                json.readValue("{\"role\":\"Admin\",\"justification\":\"confio\"}", GrantRoleRequest.class);

        assertThat(request.role()).isEqualTo(PlatformRole.ADMIN);
        assertThat(request.justification()).isEqualTo("confio");
    }

    /** Un rol que no existe sigue siendo un 400 de Jackson, que es correcto: no es un rol. */
    @Test
    void unRolInexistenteNoSeDeserializa() {
        assertThatThrownBy(() -> json.readValue("\"Wizard\"", PlatformRole.class)).isInstanceOf(Exception.class);
    }

    /**
     * Las dos puertas, una al lado de la otra. El nombre de un rol entra por el body JSON de
     * grant/revoke <em>y</em> por el texto del {@code ?q=} cuando alguien escribe {@code /role Admin}.
     * Las dos tienen que aceptar lo mismo que la API publica, o el frontend no puede devolver lo que
     * leyó — y la segunda falla más calladita: no da 400, matchea cero filas.
     */
    @Test
    void lasDosPuertasAceptanElMismoNombreDeRol() {
        for (PlatformRole role : PlatformRole.values()) {
            String published = json.writeValueAsString(role).replace("\"", "");

            assertThat(json.readValue("\"" + published + "\"", PlatformRole.class)).isEqualTo(role);
            assertThat(PlatformRole.fromRoleName(published)).contains(role);
        }
    }

    /**
     * Y difieren a propósito en cuánto perdonan: un body es exacto, una caja de búsqueda sobrevive a
     * que la escriban. Escrito acá para que sea una propiedad y no una deducción.
     */
    @Test
    void elBodyEsExactoYLaCajaDeBusquedaPerdona() {
        assertThatThrownBy(() -> json.readValue("\"ADMIN\"", PlatformRole.class)).isInstanceOf(Exception.class);

        assertThat(PlatformRole.fromRoleName("ADMIN")).contains(PlatformRole.ADMIN);
        assertThat(PlatformRole.fromRoleName("admin")).contains(PlatformRole.ADMIN);
        assertThat(PlatformRole.fromRoleName("Wizard")).isEmpty();
    }
}
