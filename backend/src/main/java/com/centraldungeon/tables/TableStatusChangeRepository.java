package com.centraldungeon.tables;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableStatusChangeRepository extends JpaRepository<TableStatusChange, String> {

    List<TableStatusChange> findByGameTable_IdOrderByCreatedAtAsc(String gameTableId);
}
