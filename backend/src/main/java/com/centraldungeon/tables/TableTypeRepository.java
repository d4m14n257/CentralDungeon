package com.centraldungeon.tables;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads the {@code table_types} table. Read-only in practice: the rows come from V2__seed.sql and
 * there is no screen that creates or deletes one yet.
 */
public interface TableTypeRepository extends JpaRepository<TableType, String> {
}
