package com.centraldungeon.tables;

import com.centraldungeon.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/**
 * How a table is run - "Public", "First class". Table {@code table_types}.
 *
 * <p>Read-only: the rows come from V2__seed.sql, and there is still no admin CRUD screen for them
 * (out of scope, unlike catalogs/). F1.1 adds the endpoint that lists them, which is what the wizard
 * of F1.2 needs and what did not exist before.
 *
 * <p>{@code status} and {@code deleted_at} exist in the baseline and stay unmapped: with two seeded
 * rows and no way to create or delete one, nothing can move them off their default.
 */
@Entity
@Table(name = "table_types")
public class TableType extends BaseEntity {

    /** The type's label, unique across the table. */
    @Column(nullable = false, unique = true, length = 64)
    private String name;

    /** What the type means, shown next to it in the wizard - "Public" alone does not explain itself. */
    @Column(length = 256)
    private @Nullable String description;

    /** Required by JPA. Nothing in the application creates a table type. */
    protected TableType() {
    }

    /**
     * Returns the type's label.
     *
     * @return the name, never null on a persisted row
     */
    public String getName() {
        return name;
    }

    /**
     * Returns what the type means.
     *
     * @return the description, or null when the row never got one
     */
    public @Nullable String getDescription() {
        return description;
    }
}
