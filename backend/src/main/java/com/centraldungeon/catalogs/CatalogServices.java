package com.centraldungeon.catalogs;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Resolves a {@link CatalogType} to the service that handles it, so /admin/catalogs can be one
 * controller with a typed path variable instead of three copies of the same six operations.
 *
 * <p>An {@code EnumMap} built in the constructor, not a scan of the application context: adding a
 * fourth catalog should fail to compile here until someone wires it, rather than silently 404 at
 * runtime.
 */
@Service
public class CatalogServices {

    /** One entry per catalog, filled in the constructor and never mutated afterwards. */
    private final Map<CatalogType, AbstractCatalogService<?>> byType = new EnumMap<>(CatalogType.class);

    /**
     * @param systemService   handles {@link CatalogType#SYSTEMS}
     * @param tagService      handles {@link CatalogType#TAGS}
     * @param platformService handles {@link CatalogType#PLATFORMS}
     */
    public CatalogServices(SystemService systemService, TagService tagService, PlatformService platformService) {
        byType.put(CatalogType.SYSTEMS, systemService);
        byType.put(CatalogType.TAGS, tagService);
        byType.put(CatalogType.PLATFORMS, platformService);
    }

    /**
     * Returns the service that handles one catalog.
     *
     * @param type the catalog, already resolved from the request path
     * @return its service
     * @throws IllegalStateException if a catalog was added to the enum and never wired here. It is a
     *                               wiring bug, not a bad request, so it is not an {@code ApiException}
     */
    public AbstractCatalogService<?> of(CatalogType type) {
        AbstractCatalogService<?> service = byType.get(type);
        if (service == null) {
            throw new IllegalStateException("No catalog service wired for " + type);
        }
        return service;
    }
}
