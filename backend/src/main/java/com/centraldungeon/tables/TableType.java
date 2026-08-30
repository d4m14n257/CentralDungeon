package com.centraldungeon.tables;

import com.centraldungeon.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Read-only in E1: rows come from V2__seed.sql, no admin CRUD screen yet (out of scope, unlike catalogs/). */
@Entity
@Table(name = "table_types")
public class TableType extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    protected TableType() {
    }

    public String getName() {
        return name;
    }
}
