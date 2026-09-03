package com.centraldungeon.catalogs;

import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/** Tags. Same shape and same rules as {@link SystemService}; only the bridge table differs. */
@Service
public class TagService extends AbstractCatalogService<Tag> {

    /** The bridge table that says which tables use a tag - the only thing this class adds. */
    private final TableTagRepository tableTagRepository;

    /**
     * @param tagRepository      the {@code tags} table
     * @param tableTagRepository the {@code table_tags} bridge table, for the usage counts
     * @param catalogMapper      the entity-to-DTO mapper shared by the three catalogs
     */
    public TagService(TagRepository tagRepository, TableTagRepository tableTagRepository, CatalogMapper catalogMapper) {
        super(tagRepository, catalogMapper);
        this.tableTagRepository = tableTagRepository;
    }

    /**
     * {@inheritDoc}
     *
     * @return always {@link CatalogType#TAGS}
     */
    @Override
    public CatalogType type() {
        return CatalogType.TAGS;
    }

    /**
     * {@inheritDoc}
     *
     * @param name the already-stripped name
     * @return a new, unsaved {@link Tag} in {@code Created}
     */
    @Override
    protected Tag newValue(String name) {
        return new Tag(name);
    }

    /**
     * {@inheritDoc}
     *
     * @param valueIds the tags to count uses for
     * @return one entry per tag with at least one live link in {@code table_tags}
     */
    @Override
    protected List<CatalogUsageCount> countUses(Collection<String> valueIds) {
        return tableTagRepository.countUsesByValueIds(valueIds);
    }
}
