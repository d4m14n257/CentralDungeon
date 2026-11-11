package com.centraldungeon.matrix;

import static com.centraldungeon.matrix.MatrixActor.ADMIN;
import static com.centraldungeon.matrix.MatrixActor.OWNER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Checks that the role matrix says an Owner can do everything an Admin can.
 *
 * <p>Owner is the same role as Admin with a wider reach, and the only thing that separates them is who
 * may move the Admin rank: granting or revoking it, and the rows that only an Owner can even attempt.
 * Everywhere else, whatever an Admin gets from an endpoint an Owner has to get too, with the same
 * status and the same error code. The integration suites check every cell over HTTP; this test checks
 * that the table of cells expresses the equality, so a row that is edited for one of the two and not
 * for the other fails here without needing a database.
 *
 * <p>The rows that are allowed to differ are listed by name with the reason. Each one must still
 * differ, so an exception that stops being true has to be removed from the list.
 */
class AdminOwnerParityTest {

    /** Every row of the four matrix classes, main rows and variants. */
    private static final List<MatrixRow> ALL_ROWS = Stream.of(
                    TablesRoleMatrixIT.ROWS,
                    RegistrationsTasksRoleMatrixIT.ROWS,
                    FilesCatalogsRoleMatrixIT.ROWS,
                    PlatformRoleMatrixIT.ROWS)
            .flatMap(List::stream)
            .toList();

    /** The rows where an Admin and an Owner are expected to get different results, with why. */
    private static final Map<String, String> MAY_DIFFER = Map.of(
            "POST /api/v1/admin/users/{id}/grant-role [role Admin]", "only an Owner grants Admin",
            "POST /api/v1/admin/users/{id}/grant-role [role Admin on another Owner]", "only an Owner moves Admin, and only an Owner is the target",
            "POST /api/v1/admin/users/{id}/grant-role [role Admin on oneself]", "an Owner cannot demote itself; the row has no Admin cell",
            "POST /api/v1/admin/users/{id}/revoke-role [role Admin]", "only an Owner revokes Admin",
            "POST /api/v1/admin/users/{id}/revoke-role [role Owner]", "only an Owner revokes Owner",
            "POST /api/v1/admin/users/{id}/revoke-role [role Owner on oneself]", "an Owner cannot revoke its own rank; the row has no Admin cell");

    @Test
    void everyMainRowDeclaresAdminAndOwner() {
        List<String> missing = ALL_ROWS.stream()
                .filter(row -> !row.isVariant())
                .filter(row -> !row.cells().containsKey(ADMIN) || !row.cells().containsKey(OWNER))
                .map(MatrixRow::name)
                .toList();

        assertThat(missing).as("main rows without an Admin or an Owner cell").isEmpty();
    }

    @Test
    void adminAndOwnerGetTheSameResultOutsideTheListedExceptions() {
        List<String> different = ALL_ROWS.stream()
                .filter(row -> !MAY_DIFFER.containsKey(row.name()))
                .filter(AdminOwnerParityTest::differsBetweenAdminAndOwner)
                .map(MatrixRow::name)
                .toList();

        assertThat(different).as("rows where Admin and Owner differ without being listed").isEmpty();
    }

    @Test
    void everyListedExceptionStillDiffers() {
        List<String> existing = ALL_ROWS.stream().map(MatrixRow::name).toList();
        assertThat(existing).as("listed exceptions that match no row").containsAll(MAY_DIFFER.keySet());

        List<String> stale = ALL_ROWS.stream()
                .filter(row -> MAY_DIFFER.containsKey(row.name()))
                .filter(row -> !differsBetweenAdminAndOwner(row))
                .map(MatrixRow::name)
                .toList();

        assertThat(stale).as("listed exceptions where Admin and Owner now get the same result").isEmpty();
    }

    /** A row where only one of the two has a cell counts as different. */
    private static boolean differsBetweenAdminAndOwner(MatrixRow row) {
        MatrixCell admin = row.cells().get(ADMIN);
        MatrixCell owner = row.cells().get(OWNER);
        if (admin == null || owner == null) {
            return admin != owner;
        }
        return admin.kind() != owner.kind()
                || admin.status() != owner.status()
                || !Objects.equals(admin.errorCode(), owner.errorCode());
    }
}
