package com.centraldungeon.features.catalogs;

import com.centraldungeon.features.catalogs.dto.AdminCatalogValueResponse;
import com.centraldungeon.features.catalogs.dto.CatalogValueResponse;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Turns catalog entities into the two response shapes. Wired as a @Bean in
 * common/config/MapperConfig.java, like every other mapper - see that class for why.
 *
 * <p>One mapper for the three catalogs: it maps {@link CatalogValue}, the shape they share, so it
 * never needs to know whether it was handed a system, a tag or a platform.
 */
@Mapper
public interface CatalogMapper {

    /**
     * The public view of a value.
     *
     * <p>{@code category} and {@code description} are resolved by the service, not here: an alias
     * reads them from its canonical entry, which takes a lookup, and a mapper never touches a
     * repository.
     *
     * @param value       the entity to describe
     * @param category    the category of its group, or null when it has none
     * @param description the definition of its group, or null when it has none
     * @return the value as everyone outside /admin/catalogs sees it
     */
    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "name", source = "value.name")
    @Mapping(target = "status", expression = "java(value.getStatus().name())")
    CatalogValueResponse toResponse(CatalogValue value, @Nullable TagCategory category, @Nullable String description);

    /**
     * The admin view of a value.
     *
     * <p>Everything past {@code value} is resolved by the service, not here: each needs a lookup,
     * and a mapper never touches a repository.
     *
     * @param value         the entity to describe
     * @param canonicalName the name of the group it belongs to, or null when it is the group's
     *                      canonical entry
     * @param uses          how many tables link to this value
     * @param aliasCount    how many values point at this one as their canonical entry
     * @param category      the category of its group, or null when it has none
     * @param description   the definition of its group, or null when it has none
     * @return the value as /admin/catalogs sees it
     */
    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "name", source = "value.name")
    @Mapping(target = "canonicalId", source = "value.canonicalId")
    @Mapping(target = "createdAt", source = "value.createdAt")
    @Mapping(target = "status", expression = "java(value.getStatus().name())")
    AdminCatalogValueResponse toAdminResponse(
            CatalogValue value,
            @Nullable String canonicalName,
            long uses,
            long aliasCount,
            @Nullable TagCategory category,
            @Nullable String description);
}
