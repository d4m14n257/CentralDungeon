package com.centraldungeon.tables;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one gate every read of a concrete table goes through, and the reason F3.4 could be built at
 * all.
 *
 * <p>Before this class there were <b>three</b> copies of the same lookup - {@code
 * GameTableService.getEntityById}, whose own Javadoc claimed to be «the single lookup every read
 * goes through» and was not, {@code TableSessionService.getTable}, and {@code
 * TableTaskService.requireLiveTable}. Each of them answered the same question ("is this table there,
 * and is it gone?") with the same four lines, which is survivable while the only answer is
 * {@code Deleted}. The veto of #29 is the second answer, and a rule with three homes is a rule that
 * gets added to two of them. §7 of {@code fase-3-admin-owner.md} named that failure in advance: «el
 * veto toca seis vías de lectura y es fácil cerrar cinco».
 *
 * <p>So the three copies became calls to this class, and the veto is written here <b>once</b>.
 * {@code ProfileVisibilityService.requireVisible} is the shape it copies: a public {@code require*}
 * that is the single place a visibility rule lives, throwing {@link NotFoundException} and never
 * {@code ForbiddenActionException} - a 403 confirms exactly what the 404 denies (#29, #249).
 *
 * <p><b>Two doors, and the narrow one is the default.</b> {@link #requireVisible} is for a read
 * somebody performs; {@link #requireExisting} is for the reads that have no reader to be vetoed -
 * a master managing their own table, an admin resolving a request - where pertenencia is checked
 * immediately afterwards and a veto is not a question that can be asked. Both go through the same
 * private load, so «the table is gone» is still decided in one place.
 */
@Service
public class TableVisibilityService {

    /** The tables themselves - this class never reaches for one any other way. */
    private final GameTableRepository gameTableRepository;

    /** Where the veto lives: a {@code Blocked} row of {@code table_registrations} (#29, #39). */
    private final TableRegistrationRepository registrationRepository;

    /**
     * @param gameTableRepository    the {@code game_tables} table
     * @param registrationRepository the {@code table_registrations} table, asked only about vetoes
     */
    public TableVisibilityService(
            GameTableRepository gameTableRepository, TableRegistrationRepository registrationRepository) {
        this.gameTableRepository = gameTableRepository;
        this.registrationRepository = registrationRepository;
    }

    /**
     * The one lookup every read of a concrete table goes through.
     *
     * <p>404 for the deleted (#25, #175) and 404 for the vetoed (#29), and deliberately the same
     * 404: from the vetoed person's side the table stopped existing, and any answer that
     * distinguishes the two tells them it is still there.
     *
     * @param gameTableId the table
     * @param actorId     who is reading, always from the token (#121)
     * @return the table
     * @throws NotFoundException when the table is not there, was soft-deleted, or the actor is
     *                          vetoed on it - never a 403 in any of the three cases
     */
    @Transactional(readOnly = true)
    public GameTable requireVisible(String gameTableId, String actorId) {
        GameTable gameTable = load(gameTableId);
        if (isVetoed(gameTableId, actorId)) {
            throw notFound(gameTableId);
        }
        return gameTable;
    }

    /**
     * The same lookup, for the reads where nobody can be vetoed.
     *
     * <p>Used by the flows whose actor is a master of the table or an admin: a master holds a row in
     * {@code masters} and the two sets are disjoint (#154), so «is this master vetoed here» has no
     * answer to give. What it still decides is the part that is common to every read - a
     * soft-deleted table is gone for everyone (#25, #175).
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
     * Whether this person is vetoed on this table (#29, #39).
     *
     * <p>The question {@link #requireVisible} cannot answer for a caller that already holds the
     * table - {@code FileService} reaches a file through a link and has the table's id but no
     * business re-loading it.
     *
     * @param gameTableId the table
     * @param actorId     the person, always from the token (#121)
     * @return true when they hold a {@code Blocked} registration on it
     */
    @Transactional(readOnly = true)
    public boolean isVetoed(String gameTableId, String actorId) {
        return registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                gameTableId, actorId, List.of(TableRegistrationStatus.Blocked));
    }

    /** The part every door shares: the row, and the soft delete that makes it gone (#25, #175). */
    private GameTable load(String gameTableId) {
        GameTable gameTable = gameTableRepository.findById(gameTableId).orElseThrow(() -> notFound(gameTableId));
        if (gameTable.getStatus() == GameTableStatus.Deleted) {
            throw notFound(gameTableId);
        }
        return gameTable;
    }

    /** One sentence for the three ways a table is not there, so none of them can be told apart. */
    private static NotFoundException notFound(String gameTableId) {
        return new NotFoundException("Table not found: " + gameTableId);
    }
}
