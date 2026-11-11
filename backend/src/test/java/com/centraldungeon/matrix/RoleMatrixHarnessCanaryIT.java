package com.centraldungeon.matrix;

import static com.centraldungeon.matrix.MatrixActor.ANONYMOUS;
import static com.centraldungeon.matrix.MatrixActor.BLOCKED;
import static com.centraldungeon.matrix.MatrixActor.PLAYER;
import static com.centraldungeon.matrix.MatrixCell.allowed;
import static com.centraldungeon.matrix.MatrixCell.denied;
import static com.centraldungeon.matrix.MatrixCell.undocumentedNot2xx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.users.PlatformRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Checks on the harness itself, without which a green matrix would mean nothing: that it fails when a
 * cell is wrong, that the blocked column is tested with a token that did work, and whether a request
 * body is validated before or after the caller's authorization is checked.
 */
class RoleMatrixHarnessCanaryIT extends AbstractRoleMatrixIT {

    /** A route every session reads and no session is refused, used as the known quantity. */
    private static final String LIMITS = "GET /api/v1/settings/limits";

    @Override
    List<MatrixRow> rows() {
        return List.of();
    }

    /**
     * A player sending an empty body to an administration route with a validated body gets the
     * validation error, not the refusal: the body is bound and validated before the method's
     * authorization runs. So an invalid body hides the authorization of every cell, which is why every
     * row has to send a valid one, and why a row's positive control is what proves its body is valid.
     */
    @Test
    void anInvalidBodyAnswersBeforeAuthorizationDoes() throws Exception {
        MatrixRow emptyBody = MatrixRow.of("POST /api/v1/admin/users/{id}/block",
                w -> MatrixRequest.path(w.person("target").getId()).json("{}"));
        MatrixRow validBody = MatrixRow.of("POST /api/v1/admin/users/{id}/block",
                w -> MatrixRequest.path(w.person("target").getId()).json("{\"justification\": \"why\"}"));

        harness().runCell(emptyBody, PLAYER, denied(400, "VALIDATION_ERROR"), null);
        harness().runCell(validBody, PLAYER, denied(403, "FORBIDDEN"), null);
    }

    /** The same token answers 200 right before the account is blocked, and is refused once it is. */
    @Test
    void theBlockedColumnHasItsPositiveControl() throws Exception {
        MatrixRow me = MatrixRow.of("GET /api/v1/users/me", w -> MatrixRequest.path());

        harness().runCell(me, BLOCKED, denied(401, "ACCOUNT_BLOCKED"), null);
        AssertionError stillPassing = assertThrows(AssertionError.class, () -> harness().runCell(me, BLOCKED, allowed(200), null));
        assertThat(stillPassing).hasMessageContaining("has to pass");
    }

    /** Every way a cell can be wrong makes it fail, and the right cell passes. */
    @Test
    void aWrongExpectationFails() {
        MatrixRow limits = MatrixRow.of(LIMITS, w -> MatrixRequest.path());
        RoleMatrixHarness harness = harness();

        assertDoesNotThrow(() -> harness.runCell(limits, PLAYER, allowed(200), null));
        assertFails("expected exactly 201", () -> harness.runCell(limits, PLAYER, allowed(201), null));
        assertFails("must not pass", () -> harness.runCell(limits, PLAYER, denied(403), null));
        assertFails("expected errorCode NOT_THE_CODE", () -> harness.runCell(limits, ANONYMOUS, denied(401, "NOT_THE_CODE"), null));
        assertFails("body check", () -> harness.runCell(limits, PLAYER, allowed(200, (w, body) -> {
            throw new AssertionError("body check");
        }), null));
        assertFails("It changed", () -> harness.runCell(limits, PLAYER, allowed(200),
                new KnownDisagreements.Entry(limits.name(), PLAYER, 403, "a canary")));
    }

    /** A request that lands anywhere but the row's own route fails, even when the status would match. */
    @Test
    void aRequestThatMissesTheRowsRouteFails() {
        RoleMatrixHarness harness = harness();
        MatrixRow noSuchRoute = MatrixRow.of("GET /api/v1/settings/limitz", w -> MatrixRequest.path());
        MatrixRow anotherRoute = MatrixRow.of("GET /api/v1/game-tables/{id}", w -> MatrixRequest.path("mine"));
        MatrixRow missingVariable = MatrixRow.of("GET /api/v1/game-tables/{id}", w -> MatrixRequest.path());

        assertFails("no controller matched", () -> harness.runCell(noSuchRoute, PLAYER, denied(404), null));
        assertFails("it reached /api/v1/game-tables/mine", () -> harness.runCell(anotherRoute, PLAYER, denied(403), null));
        assertFails("path variables", () -> harness.runCell(missingVariable, PLAYER, denied(404), null));
    }

    /** An actor holding other roles than its column says is caught before the call. */
    @Test
    void anActorWithTheWrongRolesFailsThePreflight() {
        MatrixRow limits = MatrixRow.of(LIMITS, w -> {
            w.grant(w.me(), PlatformRole.MASTER);
            return MatrixRequest.path();
        });

        assertFails("Preflight", () -> harness().runCell(limits, PLAYER, allowed(200), null));
    }

    /** A row without the base columns, or without any cell expected to pass, is rejected by its shape. */
    @Test
    void aRowWithoutItsBaseColumnsOrPositiveControlFails() {
        RoleMatrixHarness harness = harness();
        MatrixRow partial = MatrixRow.of(LIMITS, w -> MatrixRequest.path()).cell(allowed(200), PLAYER);
        MatrixRow uncontrolled = MatrixRow.of("GET /api/v1/game-tables/{id}/managed",
                        w -> MatrixRequest.path(w.table(GameTableStatus.Opened).getId()))
                .cell(denied(401), ANONYMOUS)
                .cell(undocumentedNot2xx(401), BLOCKED)
                .cell(denied(403), PLAYER, MatrixActor.MASTER, MatrixActor.ADMIN, MatrixActor.OWNER);

        assertFails("leaves base columns", () -> harness.checkShape(partial));
        assertFails("no cell expected to pass", () -> harness.checkShape(uncontrolled));
        assertDoesNotThrow(() -> harness.checkShape(uncontrolled.withoutPositiveControl("a canary")));
    }

    private static void assertFails(String because, Executable cell) {
        AssertionError error = assertThrows(AssertionError.class, cell);
        assertThat(error).hasMessageContaining(because);
    }
}
