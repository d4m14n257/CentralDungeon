package com.centraldungeon.features.catalogs;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.features.catalogs.dto.AdminCatalogValueResponse;
import com.centraldungeon.features.tables.MasterService;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tags. Same shape and same rules as {@link SystemService}, plus one thing systems and platforms do
 * not have: a tag group can carry a category and a definition, which its canonical entry stores and
 * its aliases read from there.
 */
@Service
public class TagService extends AbstractCatalogService<Tag> {

    /** The bridge table that says which tables use a tag. */
    private final TableTagRepository tableTagRepository;

    /**
     * @param tagRepository      the {@code tags} table
     * @param tableTagRepository the {@code table_tags} bridge table, for the usage counts
     * @param catalogMapper      the entity-to-DTO mapper shared by the three catalogs
     * @param masterService      answers whether a proposer writes any table
     */
    public TagService(
            TagRepository tagRepository,
            TableTagRepository tableTagRepository,
            CatalogMapper catalogMapper,
            MasterService masterService) {
        super(tagRepository, catalogMapper, masterService);
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

    /**
     * An admin says what a tag group is: its category and its definition, both replacing what the
     * group had. Only on the group's canonical entry, because that is the one row that stores them.
     *
     * @param id          the group's canonical entry
     * @param category    the category, or null for none
     * @param description the definition in plain text, or null or blank for none
     * @return the tag, with its new definition
     * @throws com.centraldungeon.common.exception.NotFoundException if no tag has that id
     * @throws ConflictException with {@link ConflictException#CATALOG_VALUE_NOT_CANONICAL} if the tag
     *                           is an alias, or with the default code if it was rejected
     */
    @Transactional
    public AdminCatalogValueResponse define(String id, @Nullable TagCategory category, @Nullable String description) {
        Tag tag = getById(id);
        if (!tag.isCanonical()) {
            throw new ConflictException(
                    "Tag " + id + " is an alias - define its canonical entry " + tag.getCanonicalId() + " instead",
                    ConflictException.CATALOG_VALUE_NOT_CANONICAL);
        }
        if (tag.getStatus() == CatalogStatus.Rejected) {
            throw new ConflictException("Tag " + id + " was rejected and has no group to define");
        }
        tag.setCategory(category);
        tag.setDescription(description == null || description.isBlank() ? null : description.strip());
        return toAdminResponse(repository.save(tag));
    }

    /**
     * {@inheritDoc}
     *
     * @param values the page's tags
     * @return tag id to its group's definition, for every tag whose group has one
     */
    @Override
    protected Map<String, CatalogDefinition> definitionsOf(List<Tag> values) {
        Map<String, Tag> heads = new HashMap<>();
        values.stream().filter(CatalogValue::isCanonical).forEach(head -> heads.put(head.getId(), head));
        Set<String> missingHeads = values.stream()
                .map(CatalogValue::getCanonicalId)
                .filter(Objects::nonNull)
                .filter(canonicalId -> !heads.containsKey(canonicalId))
                .collect(Collectors.toSet());
        if (!missingHeads.isEmpty()) {
            repository.findAllById(missingHeads).forEach(head -> heads.put(head.getId(), head));
        }

        Map<String, CatalogDefinition> definitions = new HashMap<>();
        for (Tag value : values) {
            String canonicalId = value.getCanonicalId();
            Tag head = heads.get(canonicalId == null ? value.getId() : canonicalId);
            if (head != null && (head.getCategory() != null || head.getDescription() != null)) {
                definitions.put(value.getId(), new CatalogDefinition(head.getCategory(), head.getDescription()));
            }
        }
        return definitions;
    }

    /**
     * {@inheritDoc}
     *
     * @param from the tag that stops heading the group
     * @param to   the tag that heads it from now on
     */
    @Override
    protected void handOverDefinition(Tag from, Tag to) {
        if (to.getCategory() == null) {
            to.setCategory(from.getCategory());
        }
        if (to.getDescription() == null) {
            to.setDescription(from.getDescription());
        }
        from.setCategory(null);
        from.setDescription(null);
    }
}
