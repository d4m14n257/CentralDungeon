package com.centraldungeon.users;

import com.centraldungeon.common.model.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/** No BaseEntity: the roles table has no timestamps - it is fixed reference data (V2__seed.sql). */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(length = 64)
    private @Nullable String id;

    @Column(nullable = false, unique = true, length = 32)
    private String name;

    @Column(length = 256)
    private @Nullable String description;

    protected Role() {
    }

    public Role(String name, @Nullable String description) {
        this.name = name;
        this.description = description;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
    }

    public String getId() {
        if (id == null) {
            throw new IllegalStateException("Role id is not assigned yet - it is set on persist");
        }
        return id;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }
}
