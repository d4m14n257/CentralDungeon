package com.centraldungeon.catalogs;

import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/** Platforms. Same shape and same rules as {@link SystemService}; only the bridge table differs. */
@Service
public class PlatformService extends AbstractCatalogService<Platform> {

    /** The bridge table that says which tables use a platform - the only thing this class adds. */
    private final TablePlatformRepository tablePlatformRepository;

    /**
     * @param platformRepository      the {@code platforms} table
     * @param tablePlatformRepository the {@code table_platforms} bridge table, for the usage counts
     * @param catalogMapper           the entity-to-DTO mapper shared by the three catalogs
     */
    public PlatformService(
            PlatformRepository platformRepository,
            TablePlatformRepository tablePlatformRepository,
            CatalogMapper catalogMapper) {
        super(platformRepository, catalogMapper);
        this.tablePlatformRepository = tablePlatformRepository;
    }

    /**
     * {@inheritDoc}
     *
     * @return always {@link CatalogType#PLATFORMS}
     */
    @Override
    public CatalogType type() {
        return CatalogType.PLATFORMS;
    }

    /**
     * {@inheritDoc}
     *
     * @param name the already-stripped name
     * @return a new, unsaved {@link Platform} in {@code Created}
     */
    @Override
    protected Platform newValue(String name) {
        return new Platform(name);
    }

    /**
     * {@inheritDoc}
     *
     * @param valueIds the platforms to count uses for
     * @return one entry per platform with at least one live link in {@code table_platforms}
     */
    @Override
    protected List<CatalogUsageCount> countUses(Collection<String> valueIds) {
        return tablePlatformRepository.countUsesByValueIds(valueIds);
    }
}
