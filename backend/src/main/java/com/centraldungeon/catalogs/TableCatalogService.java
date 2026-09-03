package com.centraldungeon.catalogs;

import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.common.exception.ConflictException;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a table is tagged with: its systems, its tags and its platforms.
 *
 * <p>F1.1 built the three bridge entities and the group resolution, and stopped there on purpose:
 * linking had nobody to call it until the wizard existed. This is that caller's other half.
 *
 * <p><b>The link stores the alias the master picked, never the group's canonical entry</b> (#56,
 * #58). Merging two synonyms later makes the table findable by both terms without touching a single
 * row here - which is exactly what #56 promised and what {@code CatalogGroupIT} proves.
 *
 * <p>A value still in {@code Created} <b>can</b> be linked. That is the whole point of #57: a master
 * proposes a system, tags their table with it and publishes, while the value stays invisible to
 * everyone else's filters until an admin accepts it. What cannot be linked is a value that was
 * turned down or taken out of service - and disabling one later does not break the links it already
 * has (#81).
 */
@Service
public class TableCatalogService {

    /** The statuses a value can be newly linked in. See the class note for why {@code Created} is here. */
    private static final Set<CatalogStatus> LINKABLE_STATUSES = Set.of(CatalogStatus.Created, CatalogStatus.Accepted);

    /** The {@code table_systems} bridge. */
    private final TableSystemRepository tableSystemRepository;

    /** The {@code table_tags} bridge. */
    private final TableTagRepository tableTagRepository;

    /** The {@code table_platforms} bridge. */
    private final TablePlatformRepository tablePlatformRepository;

    /** Resolves each catalog's service, to read back the values a table names. */
    private final CatalogServices catalogServices;

    /**
     * @param tableSystemRepository   the {@code table_systems} bridge
     * @param tableTagRepository      the {@code table_tags} bridge
     * @param tablePlatformRepository the {@code table_platforms} bridge
     * @param catalogServices         resolves a catalog to the service that reads its values
     */
    public TableCatalogService(
            TableSystemRepository tableSystemRepository,
            TableTagRepository tableTagRepository,
            TablePlatformRepository tablePlatformRepository,
            CatalogServices catalogServices) {
        this.tableSystemRepository = tableSystemRepository;
        this.tableTagRepository = tableTagRepository;
        this.tablePlatformRepository = tablePlatformRepository;
        this.catalogServices = catalogServices;
    }

    /**
     * Everything a table is tagged with, one entry per catalog.
     *
     * @param gameTableId the table
     * @return its live links, keyed by catalog. A catalog the table names nothing from maps to an
     *         empty list rather than being absent, so the caller never has to check twice
     */
    @Transactional(readOnly = true)
    public Map<CatalogType, List<CatalogValueResponse>> findLinks(String gameTableId) {
        Map<CatalogType, List<CatalogValueResponse>> links = new EnumMap<>(CatalogType.class);
        for (CatalogType type : CatalogType.values()) {
            links.put(type, findLinks(gameTableId, type));
        }
        return links;
    }

    /**
     * What a table is tagged with from one catalog.
     *
     * @param gameTableId the table
     * @param type        which catalog to read
     * @return its live links to that catalog, in the order they were made
     */
    @Transactional(readOnly = true)
    public List<CatalogValueResponse> findLinks(String gameTableId, CatalogType type) {
        AbstractCatalogService<?> service = catalogServices.of(type);
        return liveValueIds(gameTableId, type).stream().map(service::find).toList();
    }

    /**
     * Replaces a table's links to one catalog with exactly the values given.
     *
     * <p>Replace and not add-one-remove-one for the same reason the agenda works that way: the
     * wizard edits the set as a whole. Links that leave are marked, never dropped - the row is the
     * record of what the master once chose (#56), and marking is also what makes taking a value off
     * and putting it back an update instead of a collision with a key that still exists.
     *
     * @param gameTableId the table
     * @param type        which catalog is being set
     * @param valueIds    the values the table should end up tagged with. Empty clears the catalog.
     *                    Duplicates collapse; order is not significant
     * @throws com.centraldungeon.common.exception.NotFoundException if a value does not exist
     * @throws ConflictException if a value was rejected or taken out of service, which cannot be
     *                           linked anew (#81)
     */
    @Transactional
    public void replaceLinks(String gameTableId, CatalogType type, List<String> valueIds) {
        Set<String> wanted = new LinkedHashSet<>(valueIds);
        AbstractCatalogService<?> service = catalogServices.of(type);
        for (String valueId : wanted) {
            CatalogValueResponse value = service.find(valueId);
            if (!LINKABLE_STATUSES.contains(CatalogStatus.valueOf(value.status()))) {
                throw new ConflictException(
                        type.singular() + " '" + value.name() + "' is not available - pick another one");
            }
        }

        switch (type) {
            case SYSTEMS -> apply(
                    tableSystemRepository.findById_GameTableId(gameTableId),
                    link -> link.getId().systemId(),
                    valueId -> tableSystemRepository.save(new TableSystem(gameTableId, valueId)),
                    wanted);
            case TAGS -> apply(
                    tableTagRepository.findById_GameTableId(gameTableId),
                    link -> link.getId().tagId(),
                    valueId -> tableTagRepository.save(new TableTag(gameTableId, valueId)),
                    wanted);
            case PLATFORMS -> apply(
                    tablePlatformRepository.findById_GameTableId(gameTableId),
                    link -> link.getId().platformId(),
                    valueId -> tablePlatformRepository.save(new TablePlatform(gameTableId, valueId)),
                    wanted);
        }
    }

    /**
     * The reconciliation itself, written once for the three bridges: what is wanted and already
     * there is revived, what is wanted and missing is inserted, and what is there and no longer
     * wanted is unlinked. The three tables differ only in the name of their value column, which is
     * what {@code valueIdOf} and {@code insert} abstract away.
     */
    private <L extends TableCatalogLink> void apply(
            List<L> existingLinks, Function<L, String> valueIdOf, Consumer<String> insert, Set<String> wanted) {
        Map<String, L> existing = new LinkedHashMap<>();
        for (L link : existingLinks) {
            existing.put(valueIdOf.apply(link), link);
        }

        for (String valueId : wanted) {
            L link = existing.remove(valueId);
            if (link == null) {
                insert.accept(valueId);
            } else {
                link.setStatus(TableCatalogLinkStatus.Used);
                link.setDeletedAt(null);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (L leftover : existing.values()) {
            if (leftover.getStatus() == TableCatalogLinkStatus.Used) {
                leftover.setStatus(TableCatalogLinkStatus.Removed);
                leftover.setDeletedAt(now);
            }
        }
    }

    /** The ids a table is currently linked to in one catalog, in the order the rows come back. */
    private List<String> liveValueIds(String gameTableId, CatalogType type) {
        return switch (type) {
            case SYSTEMS -> tableSystemRepository.findById_GameTableId(gameTableId).stream()
                    .filter(link -> link.getStatus() == TableCatalogLinkStatus.Used)
                    .map(link -> link.getId().systemId())
                    .toList();
            case TAGS -> tableTagRepository.findById_GameTableId(gameTableId).stream()
                    .filter(link -> link.getStatus() == TableCatalogLinkStatus.Used)
                    .map(link -> link.getId().tagId())
                    .toList();
            case PLATFORMS -> tablePlatformRepository.findById_GameTableId(gameTableId).stream()
                    .filter(link -> link.getStatus() == TableCatalogLinkStatus.Used)
                    .map(link -> link.getId().platformId())
                    .toList();
        };
    }
}
