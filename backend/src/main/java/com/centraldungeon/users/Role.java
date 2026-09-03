package com.centraldungeon.users;

import com.centraldungeon.common.model.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/**
 * One of the four global roles. Table {@code roles}.
 *
 * <p>Fixed reference data: the four rows come from V2__seed.sql and nothing creates a fifth.
 * {@link PlatformRole} is the code's view of the same four names.
 *
 * <p>No BaseEntity: the roles table has no timestamps.
 */
@Entity
@Table(name = "roles")
public class Role {

    /** UUID v7, assigned on persist. The seeded rows carry fixed literals instead. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** The role's name - what a {@code @PreAuthorize} spells and what {@link PlatformRole} mirrors. */
    @Column(nullable = false, unique = true, length = 32)
    private String name;

    /** What the role lets someone do, in words. Shown wherever roles are granted. */
    @Column(length = 256)
    private @Nullable String description;

    /** Required by JPA. */
    protected Role() {
    }

    /**
     * @param name        the role's name, matching a {@link PlatformRole} constant
     * @param description what it lets someone do
     */
    public Role(String name, @Nullable String description) {
        this.name = name;
        this.description = description;
    }

    /** Assigns the id on insert, respecting one already set so seed data can use fixed literals. */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
    }

    /**
     * Returns the role's id.
     *
     * @return the id
     * @throws IllegalStateException if called before the row was persisted
     */
    public String getId() {
        if (id == null) {
            throw new IllegalStateException("Role id is not assigned yet - it is set on persist");
        }
        return id;
    }

    /**
     * Returns the role's name.
     *
     * @return the name, matching a {@link PlatformRole} constant
     */
    public String getName() {
        return name;
    }

    /**
     * Returns what the role lets someone do.
     *
     * @return the description, or null when the row has none
     */
    public @Nullable String getDescription() {
        return description;
    }
}
