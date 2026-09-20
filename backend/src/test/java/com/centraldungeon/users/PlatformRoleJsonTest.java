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
    void itSerializesAsTheRolesTableSpellsItAndNotAsTheConstant() {
        assertThat(json.writeValueAsString(PlatformRole.PLAYER)).isEqualTo("\"Player\"");
        assertThat(json.writeValueAsString(PlatformRole.MASTER)).isEqualTo("\"Master\"");
        assertThat(json.writeValueAsString(PlatformRole.ADMIN)).isEqualTo("\"Admin\"");
        assertThat(json.writeValueAsString(PlatformRole.OWNER)).isEqualTo("\"Owner\"");
    }

    /** What the API publishes is what the API accepts - otherwise the frontend cannot hand back what it read. */
    @Test
    void itDeserializesTheSameValueTheResponsePublishes() {
        assertThat(json.readValue("\"Player\"", PlatformRole.class)).isEqualTo(PlatformRole.PLAYER);
        assertThat(json.readValue("\"Owner\"", PlatformRole.class)).isEqualTo(PlatformRole.OWNER);
    }

    @Test
    void aGrantRoleBodyBindsWithTheRoleNameTheFrontendSends() {
        GrantRoleRequest request =
                json.readValue("{\"role\":\"Admin\",\"justification\":\"confio\"}", GrantRoleRequest.class);

        assertThat(request.role()).isEqualTo(PlatformRole.ADMIN);
        assertThat(request.justification()).isEqualTo("confio");
    }

    /** A role that does not exist is still a 400 from Jackson, which is right: it is not a role. */
    @Test
    void aRoleThatDoesNotExistIsNotDeserialized() {
        assertThatThrownBy(() -> json.readValue("\"Wizard\"", PlatformRole.class)).isInstanceOf(Exception.class);
    }

    /**
     * The two doors, side by side. A role's name comes in through the JSON body of grant/revoke <em>and</em>
     * through the text of a {@code ?q=} when somebody types {@code /role Admin}. Both have to accept what
     * the API publishes, or the frontend cannot hand back what it
     * read — and the second fails more quietly: it does not answer 400, it matches zero rows.
     */
    @Test
    void bothDoorsAcceptTheSameRoleName() {
        for (PlatformRole role : PlatformRole.values()) {
            String published = json.writeValueAsString(role).replace("\"", "");

            assertThat(json.readValue("\"" + published + "\"", PlatformRole.class)).isEqualTo(role);
            assertThat(PlatformRole.fromRoleName(published)).contains(role);
        }
    }

    /**
     * And they differ on purpose in how much they forgive: a body is exact, a search box survives being
     * typed into. Written here so it is a property and not an inference.
     */
    @Test
    void theBodyIsExactAndTheSearchBoxForgives() {
        assertThatThrownBy(() -> json.readValue("\"ADMIN\"", PlatformRole.class)).isInstanceOf(Exception.class);

        assertThat(PlatformRole.fromRoleName("ADMIN")).contains(PlatformRole.ADMIN);
        assertThat(PlatformRole.fromRoleName("admin")).contains(PlatformRole.ADMIN);
        assertThat(PlatformRole.fromRoleName("Wizard")).isEmpty();
    }
}
