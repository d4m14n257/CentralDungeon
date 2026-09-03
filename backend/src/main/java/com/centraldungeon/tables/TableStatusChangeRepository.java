package com.centraldungeon.tables;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes {@code table_status_changes} - the audit trail of a table's lifecycle. */
public interface TableStatusChangeRepository extends JpaRepository<TableStatusChange, String> {

    /**
     * A table's whole history, oldest first, which is how the status tab reads it.
     *
     * @param gameTableId the table
     * @return its status changes in chronological order. Never null, possibly empty
     */
    List<TableStatusChange> findByGameTable_IdOrderByCreatedAtAsc(String gameTableId);
}
