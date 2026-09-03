package com.centraldungeon.catalogs;

import com.centraldungeon.catalogs.dto.AdminCatalogValueResponse;
import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every rule of the three catalogs, written once (arquitectura.md 2.4, case 2). Systems, tags and
 * platforms are the same row with a different table name: same columns, same lifecycle, same six
 * admin operations, and not one rule in modelo-datos.md 5 that applies to one of them and not to the
 * other two. That is "equal by definition", not "similar today".
 *
 * <p>The extraction condition of 2.4 was met before this class existed: {@code SystemService} was
 * written whole first, and {@code TagService} and {@code PlatformService} came out identical to it
 * except for one thing - which bridge table counts a value's uses. That single difference is
 * {@link #countUses}; there is no {@code instanceof} anywhere.
 *
 * <p><b>No authorization is decided here.</b> Who may propose and who may accept is declared on each
 * concrete controller method (#123, CVE-2025-41248) - putting a {@code @PreAuthorize} on a generic
 * base is exactly the shape that CVE describes.
 *
 * <p>The rules implemented, all from modelo-datos.md 5:
 * <ul>
 *   <li>groups are flat, depth 1: an alias points at the canonical entry, never at another alias (#59)</li>
 *   <li>searching by any member resolves the whole group (#54, #56)</li>
 *   <li>masters and admins propose; only an admin accepts and classifies (#55)</li>
 *   <li>a value in {@code Created} neither shows to players nor filters; accepted, it does (#57)</li>
 *   <li>disabling a value breaks no link, and restoring it puts everything back (#81)</li>
 * </ul>
 */
public abstract class AbstractCatalogService<E extends CatalogValue> {

    /** The statuses a value can be accepted from: never reviewed, or reviewed and turned down. */
    private static final Set<CatalogStatus> ACCEPTABLE_FROM = Set.of(CatalogStatus.Created, CatalogStatus.Rejected);

    /** The statuses a value can be disabled from. Something already rejected has nothing to take out. */
    private static final Set<CatalogStatus> DISABLEABLE_FROM = Set.of(CatalogStatus.Created, CatalogStatus.Accepted);

    /** The catalog's own table. Protected so a subclass can add a read the base does not need. */
    protected final CatalogValueRepository<E> repository;

    /** Turns entities into the two response shapes. Never sees a repository (arquitectura.md 2.2). */
    private final CatalogMapper catalogMapper;

    /**
     * @param repository    the table this catalog lives in
     * @param catalogMapper the entity-to-DTO mapper shared by the three catalogs
     */
    protected AbstractCatalogService(CatalogValueRepository<E> repository, CatalogMapper catalogMapper) {
        this.repository = repository;
        this.catalogMapper = catalogMapper;
    }

    /**
     * Which catalog this is - only used to name things in error messages.
     *
     * @return the concrete catalog this service handles
     */
    public abstract CatalogType type();

    /**
     * Builds a value of the concrete catalog. The base never knows the concrete class.
     *
     * @param name the already-stripped name to give it
     * @return a new, unsaved value in {@code Created}
     */
    protected abstract E newValue(String name);

    /**
     * The one genuine difference between the three: which bridge table holds their links.
     *
     * @param valueIds the values to count uses for
     * @return one entry per value that has at least one live link; the rest are simply absent
     */
    protected abstract List<CatalogUsageCount> countUses(Collection<String> valueIds);

    // ----------------------------------------------------------------- reads

    /**
     * What a master's combobox and a player's filter see: accepted values only (#57, #81). Anything
     * proposed, rejected or disabled is invisible here on purpose - the whole point of #57 is that a
     * value nobody reviewed does not get to pollute what everyone else searches.
     *
     * @param query    the search box, in the language of {@code common/search} (#164), or null for
     *                 everything. An unknown {@code /field} is searched as literal text, never a 400
     * @param pageable page, size and sort. Callers pass a tie-break by id (#171)
     * @return one page of accepted values
     */
    @Transactional(readOnly = true)
    public PageResponse<CatalogValueResponse> search(@Nullable String query, Pageable pageable) {
        SearchQuery parsed = SearchQueryParser.parse(query, CatalogSearchField.wireNames());
        Page<E> page = repository.findAll(CatalogSearchSpecification.accepted(parsed), pageable);
        return PageResponse.from(page.map(catalogMapper::toResponse));
    }

    /**
     * One value by id, whatever its status. Not filtered to accepted like {@link #search}, because
     * this is how a master reads back the value he just proposed - and #57 asks the interface to say
     * "pending", which it can only do if it gets the value and its status instead of a 404.
     *
     * @param id the value to read
     * @return the value with its status
     * @throws com.centraldungeon.common.exception.NotFoundException if no value has that id
     */
    @Transactional(readOnly = true)
    public CatalogValueResponse find(String id) {
        return catalogMapper.toResponse(getById(id));
    }

    /**
     * /admin/catalogs: everything, whatever its status, plus what the admin needs to decide on it.
     *
     * @param query    the search box, same language as {@link #search}, or null for everything
     * @param statuses the statuses to keep. Empty means no status filter at all - which is the
     *                 default, because reviewing what was proposed is the point of the screen
     * @param pageable page, size and sort
     * @return one page of values with their group and their usage count
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminCatalogValueResponse> adminSearch(
            @Nullable String query, List<CatalogStatus> statuses, Pageable pageable) {
        SearchQuery parsed = SearchQueryParser.parse(query, CatalogSearchField.wireNames());
        Page<E> page = repository.findAll(CatalogSearchSpecification.forAdmin(parsed, statuses), pageable);

        List<E> values = page.getContent();
        Map<String, String> canonicalNames = canonicalNamesOf(values);
        Map<String, Long> uses = usesOf(values);

        return PageResponse.from(page.map(value -> toAdminResponse(value, canonicalNames, uses)));
    }

    /**
     * A whole synonym group, in one read: the canonical entry first, then its aliases.
     *
     * <p><b>Every member, whatever its status</b> - unlike {@link #resolveGroupIds}, which is what a
     * search filters by and therefore only carries accepted values. This one is what /admin/catalogs
     * needs to answer two questions it cannot answer from a single row: which synonyms a value has,
     * and who can take over the group when its canonical entry is disabled (#59).
     *
     * <p>Not paginated: depth is 1 (#59), so a group is bounded by how many synonyms a community
     * writes for one thing, and it is read as a whole or not at all.
     *
     * @param valueId any member of the group, canonical entry or alias alike
     * @return the group, canonical entry first
     * @throws com.centraldungeon.common.exception.NotFoundException if no value has that id
     */
    @Transactional(readOnly = true)
    public List<AdminCatalogValueResponse> group(String valueId) {
        E value = getById(valueId);
        String rootId = value.isCanonical() ? value.getId() : requireNonNullCanonical(value);
        List<E> members = repository.findByIdOrCanonicalId(rootId, rootId);
        Map<String, String> canonicalNames = canonicalNamesOf(members);
        Map<String, Long> uses = usesOf(members);
        return members.stream()
                .sorted(Comparator.comparing((E member) -> !member.isCanonical()).thenComparing(CatalogValue::getName))
                .map(member -> toAdminResponse(member, canonicalNames, uses))
                .toList();
    }

    /**
     * Every accepted id equivalent to this one, the value itself included - what a filter by catalog
     * has to match against (#54, #56). Depth is 1, so the group is one query and no recursion.
     *
     * <p>Non-accepted members are left out: a disabled or still-unreviewed synonym does not filter
     * (#57, #81). The result can be empty - that is the correct answer for a group nobody accepted.
     *
     * @param valueId any member of the group, canonical entry or alias alike
     * @return the accepted ids of the whole group, possibly empty
     * @throws com.centraldungeon.common.exception.NotFoundException if no value has that id
     */
    @Transactional(readOnly = true)
    public List<String> resolveGroupIds(String valueId) {
        E value = getById(valueId);
        String rootId = value.isCanonical() ? value.getId() : requireNonNullCanonical(value);
        return repository.findByIdOrCanonicalId(rootId, rootId).stream()
                .filter(member -> member.getStatus() == CatalogStatus.Accepted)
                .map(CatalogValue::getId)
                .toList();
    }

    // ----------------------------------------------------- proposing a value

    /**
     * A master or an admin proposes a value (#55). It is born in {@code Created}: it does not show
     * to players and does not filter until an admin accepts it (#57), but the table that uses it
     * publishes anyway - that decoupling is the reason #57 exists.
     *
     * @param rawName the name as it was typed; surrounding whitespace is stripped here
     * @return the created value, in {@code Created}
     * @throws com.centraldungeon.common.exception.ConflictException if the name is already taken,
     *                                                              ignoring case
     */
    @Transactional
    public CatalogValueResponse propose(String rawName) {
        String name = rawName.strip();
        repository.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw new ConflictException(
                    type().singular() + " '" + existing.getName() + "' already exists - pick it instead of proposing it again");
        });
        return catalogMapper.toResponse(repository.save(newValue(name)));
    }

    // -------------------------------------------------- the admin operations

    /**
     * Accept a proposal and classify it in the same step (#55): either it is a canonical entry of
     * its own, or it joins an existing group as an alias.
     *
     * <p>The target has to be canonical itself. That is the depth-1 invariant of #59, and enforcing
     * it here is what makes cycles ({@code A -> B -> A}) impossible instead of merely unlikely.
     *
     * @param id          the proposal to accept
     * @param canonicalId the group to join, or null to accept it as a canonical entry of its own
     * @return the accepted value, with its group resolved
     * @throws com.centraldungeon.common.exception.NotFoundException if the value or the target does
     *                                                              not exist
     * @throws com.centraldungeon.common.exception.ConflictException if the value is not pending, if
     *                                                              the target is an alias or is not
     *                                                              accepted, or if it is the value
     *                                                              itself
     */
    @Transactional
    public AdminCatalogValueResponse accept(String id, @Nullable String canonicalId) {
        E value = getById(id);
        if (!ACCEPTABLE_FROM.contains(value.getStatus())) {
            throw new ConflictException(
                    type().singular() + " '" + value.getName() + "' is " + value.getStatus() + " and cannot be accepted"
                            + (value.getStatus() == CatalogStatus.Disabled ? " - restore it instead" : ""));
        }
        value.setCanonicalId(canonicalId == null ? null : validatedCanonicalTarget(value, canonicalId).getId());
        value.setStatus(CatalogStatus.Accepted);
        value.setDeletedAt(null);
        return toAdminResponse(repository.save(value));
    }

    /**
     * Reject a proposal: it never shows and never filters (#57).
     *
     * <p>Only from {@code Created}. Taking an accepted value out of circulation is
     * {@link #disable}, and the two are not interchangeable - one says "this should never have been
     * proposed", the other says "this was fine and is no longer in use".
     *
     * @param id the proposal to turn down
     * @return the rejected value
     * @throws com.centraldungeon.common.exception.NotFoundException if no value has that id
     * @throws com.centraldungeon.common.exception.ConflictException if the value is not pending
     */
    @Transactional
    public AdminCatalogValueResponse reject(String id) {
        E value = getById(id);
        if (value.getStatus() != CatalogStatus.Created) {
            throw new ConflictException(
                    type().singular() + " '" + value.getName() + "' is " + value.getStatus()
                            + " and cannot be rejected - only a pending proposal can");
        }
        value.setStatus(CatalogStatus.Rejected);
        return toAdminResponse(repository.save(value));
    }

    /**
     * Merge two synonym groups: the source stops being a group and everything it held - its aliases
     * and itself - points at the target (#55, #59).
     *
     * <p>Not one row of table_systems / table_tags / table_platforms is touched, and that is the
     * point of #56: a table tagged "DANDD" becomes findable by "D&amp;D 5e" the moment this runs,
     * without migrating anything.
     *
     * @param sourceCanonicalId the group that stops being one
     * @param targetCanonicalId the group that survives
     * @return the surviving group's canonical entry - the row the screen has to refresh
     * @throws com.centraldungeon.common.exception.NotFoundException if either id does not exist
     * @throws com.centraldungeon.common.exception.ConflictException if the two are the same, if
     *                                                              either is an alias rather than a
     *                                                              group, or if either is not
     *                                                              accepted
     */
    @Transactional
    public AdminCatalogValueResponse merge(String sourceCanonicalId, String targetCanonicalId) {
        if (sourceCanonicalId.equals(targetCanonicalId)) {
            throw new ConflictException("A group cannot be merged into itself");
        }
        E source = getById(sourceCanonicalId);
        E target = getById(targetCanonicalId);
        requireCanonical(source, "source");
        requireCanonical(target, "target");
        requireAccepted(source);
        requireAccepted(target);

        List<E> aliases = repository.findByCanonicalId(source.getId());
        aliases.forEach(alias -> alias.setCanonicalId(target.getId()));
        source.setCanonicalId(target.getId());

        repository.saveAll(aliases);
        repository.save(source);
        return toAdminResponse(target);
    }

    /**
     * Take one alias out of its group: it becomes a canonical entry of its own (#55).
     *
     * @param memberId the alias that leaves
     * @return the value, now canonical
     * @throws com.centraldungeon.common.exception.NotFoundException if no value has that id
     * @throws com.centraldungeon.common.exception.ConflictException if it is already a canonical
     *                                                              entry, and so has no group to
     *                                                              leave
     */
    @Transactional
    public AdminCatalogValueResponse split(String memberId) {
        E member = getById(memberId);
        if (member.isCanonical()) {
            throw new ConflictException(
                    type().singular() + " '" + member.getName() + "' is already a canonical entry - it has no group to leave");
        }
        member.setCanonicalId(null);
        return toAdminResponse(repository.save(member));
    }

    /**
     * Take a value out of circulation (#81). Logical, never physical: the links that point at it
     * keep their rows, so {@link #restore} puts everything back with no migration.
     *
     * <p>Disabling a canonical entry that still has live aliases <b>is</b> changing the group's
     * canonical (#59), so it needs a successor, and the admin picks it (#55) - never an arbitrary
     * "first alias". The disabled value then stays in the group as an alias of the successor, which
     * is what keeps the tables tagged with it findable by the rest of the group.
     *
     * @param id             the value to take out of circulation
     * @param newCanonicalId the member that takes over the group, required only when the value is a
     *                       canonical entry with live aliases; ignored otherwise
     * @return the disabled value
     * @throws com.centraldungeon.common.exception.NotFoundException if no value has that id
     * @throws com.centraldungeon.common.exception.ConflictException if the value is already disabled
     *                                                              or was rejected, or if a
     *                                                              successor is needed and the one
     *                                                              given is missing or not a live
     *                                                              member of the group
     */
    @Transactional
    public AdminCatalogValueResponse disable(String id, @Nullable String newCanonicalId) {
        E value = getById(id);
        if (!DISABLEABLE_FROM.contains(value.getStatus())) {
            throw new ConflictException(
                    type().singular() + " '" + value.getName() + "' is " + value.getStatus() + " and cannot be disabled");
        }

        List<E> aliases = value.isCanonical() ? liveAliasesOf(value) : List.of();
        if (!aliases.isEmpty()) {
            E successor = validatedSuccessor(value, aliases, newCanonicalId);
            successor.setCanonicalId(null);
            aliases.stream()
                    .filter(alias -> !alias.getId().equals(successor.getId()))
                    .forEach(alias -> alias.setCanonicalId(successor.getId()));
            value.setCanonicalId(successor.getId());
            repository.saveAll(aliases);
        }

        value.setStatus(CatalogStatus.Disabled);
        value.setDeletedAt(LocalDateTime.now());
        return toAdminResponse(repository.save(value));
    }

    /**
     * Put a disabled value back in circulation, in the group it was in (#81).
     *
     * @param id the value to bring back
     * @return the restored value, accepted again
     * @throws com.centraldungeon.common.exception.NotFoundException if no value has that id
     * @throws com.centraldungeon.common.exception.ConflictException if the value is not disabled
     */
    @Transactional
    public AdminCatalogValueResponse restore(String id) {
        E value = getById(id);
        if (value.getStatus() != CatalogStatus.Disabled) {
            throw new ConflictException(
                    type().singular() + " '" + value.getName() + "' is " + value.getStatus() + " and is not disabled");
        }
        value.setStatus(CatalogStatus.Accepted);
        value.setDeletedAt(null);
        return toAdminResponse(repository.save(value));
    }

    // ---------------------------------------------------------------- shared

    /**
     * Loads a value or fails naming what was missing.
     *
     * @param id the value to load
     * @return the entity
     * @throws NotFoundException if no value has that id
     */
    protected E getById(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException(type().singular() + " " + id + " not found"));
    }

    /**
     * The target of a canonical_id must itself be canonical, accepted, and not the value being
     * classified (#59).
     *
     * @param value       the value being classified
     * @param canonicalId the group it wants to join
     * @return the validated target
     * @throws ConflictException if the target is the value itself, an alias, or not accepted
     */
    private E validatedCanonicalTarget(E value, String canonicalId) {
        if (canonicalId.equals(value.getId())) {
            throw new ConflictException(type().singular() + " '" + value.getName() + "' cannot be its own canonical entry");
        }
        E target = getById(canonicalId);
        requireCanonical(target, "canonical");
        requireAccepted(target);
        return target;
    }

    /**
     * Resolves who takes over a group whose canonical entry is being disabled. The admin picks, so a
     * missing choice is an error rather than a default (#55).
     *
     * @param value          the canonical entry being disabled
     * @param aliases        the group's live aliases, guaranteed non-empty by the caller
     * @param newCanonicalId the successor the admin picked, or null if none was sent
     * @return the alias that becomes the new canonical entry
     * @throws ConflictException if no successor was sent, or the one sent is not in the group
     */
    private E validatedSuccessor(E value, List<E> aliases, @Nullable String newCanonicalId) {
        if (newCanonicalId == null) {
            throw new ConflictException(
                    type().singular() + " '" + value.getName() + "' is the canonical entry of a group with "
                            + aliases.size() + " live alias(es) - pick which one takes over before disabling it");
        }
        return aliases.stream()
                .filter(alias -> alias.getId().equals(newCanonicalId))
                .findFirst()
                .orElseThrow(() -> new ConflictException(
                        "The new canonical entry has to be a live member of the group being changed"));
    }

    /**
     * The aliases of a group that are still in circulation. Disabled or rejected members do not
     * count: they are not what makes a group need a successor.
     *
     * @param canonical the group's canonical entry
     * @return its accepted aliases, possibly empty
     */
    private List<E> liveAliasesOf(E canonical) {
        return repository.findByCanonicalId(canonical.getId()).stream()
                .filter(alias -> alias.getStatus() == CatalogStatus.Accepted)
                .toList();
    }

    /**
     * Guards the depth-1 invariant (#59): only a canonical entry can play the role being checked.
     *
     * @param value the value to check
     * @param role  what it was being used as ("source", "target", "canonical"), to name it in the
     *              error the admin reads
     * @throws ConflictException if the value is an alias
     */
    private void requireCanonical(E value, String role) {
        if (!value.isCanonical()) {
            throw new ConflictException(
                    "The " + role + " has to be a canonical entry: '" + value.getName()
                            + "' is an alias, and a group is always one level deep");
        }
    }

    /**
     * Guards that a value is in circulation before another one is attached to it.
     *
     * @param value the value to check
     * @throws ConflictException if it is anything other than accepted
     */
    private void requireAccepted(E value) {
        if (value.getStatus() != CatalogStatus.Accepted) {
            throw new ConflictException(
                    type().singular() + " '" + value.getName() + "' is " + value.getStatus() + ", not an accepted value");
        }
    }

    /**
     * Reads the canonical id of a value the caller already knows is an alias, so the compiler stops
     * asking about null.
     *
     * @param value an alias
     * @return its canonical entry's id
     * @throws IllegalStateException if it turns out not to be an alias - a state the catalog cannot
     *                               be in, and a bug rather than a bad request
     */
    private String requireNonNullCanonical(E value) {
        String canonicalId = value.getCanonicalId();
        if (canonicalId == null) {
            throw new IllegalStateException("An alias without a canonical entry is not a state this catalog can be in");
        }
        return canonicalId;
    }

    /**
     * Single-value flavour: one extra lookup is fine outside a listing.
     *
     * @param value the value to describe
     * @return the admin view of it, with its canonical name and usage count resolved
     */
    private AdminCatalogValueResponse toAdminResponse(E value) {
        return toAdminResponse(value, canonicalNamesOf(List.of(value)), usesOf(List.of(value)));
    }

    /**
     * Builds the admin view from lookups already resolved for the whole page.
     *
     * @param value          the value to describe
     * @param canonicalNames canonical id to name, for the page
     * @param uses           value id to usage count, for the page. A missing entry means zero
     * @return the admin view of the value
     */
    private AdminCatalogValueResponse toAdminResponse(E value, Map<String, String> canonicalNames, Map<String, Long> uses) {
        String canonicalId = value.getCanonicalId();
        return catalogMapper.toAdminResponse(
                value, canonicalId == null ? null : canonicalNames.get(canonicalId), uses.getOrDefault(value.getId(), 0L));
    }

    /**
     * Resolves the canonical entries' names for a whole page. One query instead of one per row.
     *
     * @param values the page's values
     * @return canonical id to name, empty when none of them belongs to a group
     */
    private Map<String, String> canonicalNamesOf(List<E> values) {
        Set<String> canonicalIds = values.stream()
                .map(CatalogValue::getCanonicalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (canonicalIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        repository.findAllById(canonicalIds).forEach(canonical -> names.put(canonical.getId(), canonical.getName()));
        return names;
    }

    /**
     * Resolves the usage counts for a whole page, through the concrete service's bridge table.
     *
     * @param values the page's values
     * @return value id to usage count. A value nothing points at is absent, and reads as zero
     */
    private Map<String, Long> usesOf(List<E> values) {
        List<String> ids = values.stream().map(CatalogValue::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return countUses(ids).stream()
                .collect(Collectors.toMap(CatalogUsageCount::valueId, CatalogUsageCount::uses));
    }
}
