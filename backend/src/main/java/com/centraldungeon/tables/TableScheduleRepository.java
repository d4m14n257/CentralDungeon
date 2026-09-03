package com.centraldungeon.tables;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes {@code table_schedules}, a table's weekly agenda in UTC (#22). */
public interface TableScheduleRepository extends JpaRepository<TableSchedule, TableScheduleId> {

    /**
     * Every slot ever added to a table, live or removed.
     *
     * <p>The removed ones matter: slots are marked and not dropped, so putting a slot back means
     * finding its old row and reviving it rather than inserting a key that already exists.
     *
     * @param gameTableId the table
     * @return all of its slots, whatever their status
     */
    List<TableSchedule> findById_GameTableId(String gameTableId);

    /**
     * A table's agenda as it stands.
     *
     * @param gameTableId the table
     * @param status      {@code Created} for the live agenda
     * @return the slots in that status
     */
    List<TableSchedule> findById_GameTableIdAndStatus(String gameTableId, TableScheduleStatus status);

    /**
     * The agendas of several tables in one round trip - what the clash check of #178 needs, since it
     * compares one table against every other table a person is committed to.
     *
     * @param gameTableIds the tables to read the agenda of. An empty collection returns nothing
     * @param status       {@code Created} for the live agendas
     * @return every slot of those tables in that status, in no particular order
     */
    List<TableSchedule> findById_GameTableIdInAndStatus(Collection<String> gameTableIds, TableScheduleStatus status);
}
