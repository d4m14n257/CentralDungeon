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
 * <p>{@code code} is what separates a row the application shipped from one a person will type
 * (#225). The seeded pair carries {@code PUBLIC} and {@code FIRST_CLASS} and the frontend renders
 * their translation; a type an admin adds later has none, and its {@code name} is read verbatim -
 * the same rule that already governs {@code systems}, {@code tags} and {@code platforms}.
 *
 * <p>{@code status} and {@code deleted_at} exist in the baseline and stay unmapped: with two seeded
 * rows and no way to create or delete one, nothing can move them off their default.
 */
@Entity
@Table(name = "table_types")
public class TableType extends BaseEntity {

    /** The type's label, unique across the table. What a reader sees when there is no {@link #code}. */
    @Column(nullable = false, unique = true, length = 64)
    private String name;

    /**
     * The stable identifier of a type the application shipped, or null for one a person created
     * (#225). Never shown: it is what the frontend translates by.
     */
    @Column(unique = true, length = 32)
    private @Nullable String code;

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

    /**
     * Returns the identifier the frontend translates by.
     *
     * @return the code of a type the application shipped, or null when a person named this one
     */
    public @Nullable String getCode() {
        return code;
    }
}
