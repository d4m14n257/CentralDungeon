package com.centraldungeon.tables;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code masters} table - the source of truth for who runs a table (#135). */
public interface MasterRepository extends JpaRepository<Master, MasterId> {

    /**
     * Everyone who runs a table.
     *
     * @param gameTableId the table
     * @return its master rows, live and deleted alike; the caller filters by status
     */
    List<Master> findByGameTable_Id(String gameTableId);

    /**
     * The membership check behind every "may this person touch this table" decision (#121, #135).
     *
     * @param gameTableId the table
     * @param userId      the actor, always taken from the token and never from the URL
     * @return their master row, or empty when they do not run this table
     */
    Optional<Master> findByGameTable_IdAndUser_Id(String gameTableId, String userId);

    /**
     * Whether someone runs any table at all - what the context switcher uses to decide if the Master
     * context is worth offering (#135).
     *
     * @param userId the person to check
     * @return true when they have at least one master row
     */
    boolean existsByUser_Id(String userId);

    /**
     * Row-locked read used by MasterService before flipping who is Primary (modelo-datos.md #73):
     * concurrent requests on the same table serialize here instead of racing past each other.
     *
     * @param gameTableId the table whose master rows are being changed
     * @return its master rows, locked for the rest of the transaction
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Master m where m.gameTable.id = :gameTableId")
    List<Master> findByGameTableIdForUpdate(@Param("gameTableId") String gameTableId);
}
