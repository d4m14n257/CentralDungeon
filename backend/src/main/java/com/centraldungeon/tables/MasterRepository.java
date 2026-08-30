package com.centraldungeon.tables;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MasterRepository extends JpaRepository<Master, MasterId> {

    List<Master> findByGameTable_Id(String gameTableId);

    Optional<Master> findByGameTable_IdAndUser_Id(String gameTableId, String userId);

    /**
     * Row-locked read used by MasterService before flipping who is Primary (modelo-datos.md #73):
     * concurrent requests on the same table serialize here instead of racing past each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Master m where m.gameTable.id = :gameTableId")
    List<Master> findByGameTableIdForUpdate(@Param("gameTableId") String gameTableId);
}
