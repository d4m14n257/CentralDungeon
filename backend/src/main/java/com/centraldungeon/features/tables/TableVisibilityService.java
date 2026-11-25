package com.centraldungeon.features.tables;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.features.registrations.TableRegistrationRepository;
import com.centraldungeon.features.registrations.TableRegistrationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one gate every read of a concrete table goes through, and the reason a veto can be
 * added in one place.
 *
 * <p>Before this class there were <b>three</b> copies of the same lookup - {@code
 * GameTableService.getEntityById}, whose own Javadoc claimed to be «the single lookup every read
 * goes through» and was not, {@code TableSessionService.getTable}, and {@code
 * TableTaskService.requireLiveTable}. Each of them answered the same question ("is this table there,
 * and is it gone?") with the same four lines, which is survivable while the only answer is
 * {@code Deleted}. The veto (a blocked player no longer sees the table) is the second answer, and a rule with
 * three homes is a rule that gets added to two of them: the veto touches six read paths and it is
 * easy to close five.
 *
 * <p>So the three copies became calls to this class, and the veto is written here <b>once</b>.
 * {@code ProfileVisibilityService.requireVisible} is the shape it copies: a public {@code require*}
 * that is the single place a visibility rule lives, throwing {@link NotFoundException} and never
 * {@code ForbiddenActionException} - a 403 confirms exactly what the 404 denies.
 *
 * <p><b>Two doors, and the narrow one is the default.</b> {@link #requireVisible} is for a read
 * somebody performs; {@link #requireExisting} is for the reads that have no reader to be vetoed -
 * a master managing their own table, an admin resolving a request - where membership is checked
 * immediately afterwards and a veto is not a question that can be asked. Both go through the same
 * private load, so «the table is gone» is still decided in one place.
 */
@Service
public class TableVisibilityService {

    /** The tables themselves - this class never reaches for one any other way. */
    private final GameTableRepository gameTableRepository;

    /** Where the veto lives: a {@code Blocked} row of {@code table_registrations}. */
    private final TableRegistrationRepository registrationRepository;

    /** Who may still read a table that was never public: its masters and the admins. */
    private final MasterService masterService;

    /**
     * @param gameTableRepository    the {@code game_tables} table
     * @param registrationRepository the {@code table_registrations} table, asked only about vetoes
     * @param masterService          answers whether the reader runs the table or is an admin
     */
    public TableVisibilityService(
            GameTableRepository gameTableRepository,
            TableRegistrationRepository registrationRepository,
            MasterService masterService) {
        this.gameTableRepository = gameTableRepository;
        this.registrationRepository = registrationRepository;
        this.masterService = masterService;
    }

    /**
     * The one lookup every read of a concrete table goes through.
     *
     * <p>404 for the deleted, 404 for the vetoed, and 404 for a
     * table that was never public when the reader neither runs it nor is an admin -
     * deliberately the same 404: any answer that distinguishes them tells the reader the table is there.
     *
     * @param gameTableId the table
     * @param actorId     who is reading, always from the token
     * @return the table
     * @throws NotFoundException when the table is not there, was soft-deleted, was never public and
     *                          the actor is outside it, or the actor is vetoed on it - never a 403
     */
    @Transactional(readOnly = true)
    public GameTable requireVisible(String gameTableId, String actorId) {
        GameTable gameTable = load(gameTableId);
        if (!visibleTo(gameTable, gameTableId, actorId)) {
            throw notFound(gameTableId);
        }
        return gameTable;
    }

    /**
     * The same question as {@link #requireVisible}, answered rather than thrown.
     *
     * <p>For a caller that reaches a table through something attached to it - a file a table shares -
     * and has to skip that table instead of failing: the attachment is readable exactly when the table
     * itself would be, so the deleted, the never-public and the vetoed cases all count as "no".
     *
     * @param gameTableId the table
     * @param actorId     who is reading, always from the token
     * @return true when {@link #requireVisible} would return the table to this reader
     */
    @Transactional(readOnly = true)
    public boolean canSee(String gameTableId, String actorId) {
        return findLive(gameTableId).map(gameTable -> visibleTo(gameTable, gameTableId, actorId)).orElse(false);
    }

    /**
     * The same lookup, for the reads where nobody can be vetoed.
     *
     * <p>Used by the flows whose actor is a master of the table or an admin: a master holds a row in
     * {@code masters} and the two sets are disjoint, so «is this master vetoed here» has no
     * answer to give. What it still decides is the part that is common to every read - a
     * soft-deleted table is gone for everyone.
     *
     * <p>It exists so that a caller that genuinely has no reader does not have to invent one; it is
     * <b>not</b> an escape hatch for a public read. Anything a player can reach uses
     * {@link #requireVisible}.
     *
     * @param gameTableId the table
     * @return the table
     * @throws NotFoundException when the table is not there or was soft-deleted
     */
    @Transactional(readOnly = true)
    public GameTable requireExisting(String gameTableId) {
        return load(gameTableId);
    }

    /**
     * Whether this person is vetoed on this table.
     *
     * <p>Only one part of what {@link #requireVisible} and {@link #canSee} decide: a caller that needs
     * to know whether somebody may read the table asks one of those, not this.
     *
     * @param gameTableId the table
     * @param actorId     the person, always from the token
     * @return true when they hold a {@code Blocked} registration on it
     */
    @Transactional(readOnly = true)
    public boolean isVetoed(String gameTableId, String actorId) {
        return registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                gameTableId, actorId, List.of(TableRegistrationStatus.Blocked));
    }

    /** The part every door shares: the row, and the soft delete that makes it gone. */
    private GameTable load(String gameTableId) {
        return findLive(gameTableId).orElseThrow(() -> notFound(gameTableId));
    }

    private Optional<GameTable> findLive(String gameTableId) {
        return gameTableRepository.findById(gameTableId).filter(gameTable -> gameTable.getStatus() != GameTableStatus.Deleted);
    }

    /**
     * A table nobody was shown yet is not there for anyone outside it, and a vetoed reader does not see
     * the table they were vetoed from - both read as the same absence as a deleted table.
     */
    private boolean visibleTo(GameTable gameTable, String gameTableId, String actorId) {
        if (gameTable.getStatus().wasNeverPublic() && !masterService.canOversee(gameTableId, actorId)) {
            return false;
        }
        return !isVetoed(gameTableId, actorId);
    }

    /** One sentence for the three ways a table is not there, so none of them can be told apart. */
    private static NotFoundException notFound(String gameTableId) {
        return new NotFoundException("Table not found: " + gameTableId);
    }
}
