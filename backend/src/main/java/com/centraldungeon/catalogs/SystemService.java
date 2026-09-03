package com.centraldungeon.catalogs;

import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Game systems. This is the one that was written whole first; {@link AbstractCatalogService} is what
 * was left after {@link TagService} and {@link PlatformService} came out identical to it
 * (arquitectura.md 2.4).
 */
@Service
public class SystemService extends AbstractCatalogService<GameSystem> {

    /** The bridge table that says which tables use a system - the only thing this class adds. */
    private final TableSystemRepository tableSystemRepository;

    /**
     * @param systemRepository      the {@code systems} table
     * @param tableSystemRepository the {@code table_systems} bridge table, for the usage counts
     * @param catalogMapper         the entity-to-DTO mapper shared by the three catalogs
     */
    public SystemService(
            SystemRepository systemRepository, TableSystemRepository tableSystemRepository, CatalogMapper catalogMapper) {
        super(systemRepository, catalogMapper);
        this.tableSystemRepository = tableSystemRepository;
    }

    /**
     * {@inheritDoc}
     *
     * @return always {@link CatalogType#SYSTEMS}
     */
    @Override
    public CatalogType type() {
        return CatalogType.SYSTEMS;
    }

    /**
     * {@inheritDoc}
     *
     * @param name the already-stripped name
     * @return a new, unsaved {@link GameSystem} in {@code Created}
     */
    @Override
    protected GameSystem newValue(String name) {
        return new GameSystem(name);
    }

    /**
     * {@inheritDoc}
     *
     * @param valueIds the systems to count uses for
     * @return one entry per system with at least one live link in {@code table_systems}
     */
    @Override
    protected List<CatalogUsageCount> countUses(Collection<String> valueIds) {
        return tableSystemRepository.countUsesByValueIds(valueIds);
    }
}
