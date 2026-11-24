package com.centraldungeon.features.catalogs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/**
 * A free-form label a master puts on their table ("Principiantes", "One-shot"). Table {@code tags}.
 *
 * <p>Unlike systems and platforms, a tag group can say what it is: a category and a short
 * definition, written by an admin. Both belong to the group, so only the canonical entry stores
 * them; on an alias they stay empty and are read from its canonical entry.
 */
@Entity
@Table(name = "tags")
public class Tag extends CatalogValue {

    /** What kind of thing the group says about a table, or null while nobody has classified it. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private @Nullable TagCategory category;

    /** What the group means, in plain text, or null while nobody has defined it. */
    @Column(length = 500)
    private @Nullable String description;

    /** Required by JPA. */
    protected Tag() {
    }

    /**
     * Builds a tag that has not been reviewed yet: no group, no definition, status {@code Created}.
     *
     * @param name the tag as its author wrote it
     */
    public Tag(String name) {
        super(name);
    }

    /**
     * Returns the category this tag stores. Meaningful only on a canonical entry.
     *
     * @return the category, or null when it has none
     */
    public @Nullable TagCategory getCategory() {
        return category;
    }

    /**
     * Sets the category this tag stores.
     *
     * @param category the new category, or null to leave it unclassified
     */
    public void setCategory(@Nullable TagCategory category) {
        this.category = category;
    }

    /**
     * Returns the definition this tag stores. Meaningful only on a canonical entry.
     *
     * @return the definition in plain text, or null when it has none
     */
    public @Nullable String getDescription() {
        return description;
    }

    /**
     * Sets the definition this tag stores.
     *
     * @param description the new definition in plain text, or null to clear it
     */
    public void setDescription(@Nullable String description) {
        this.description = description;
    }
}
