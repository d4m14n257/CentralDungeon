package com.centraldungeon.features.catalogs;

import com.centraldungeon.features.catalogs.dto.AdminCatalogValueResponse;
import com.centraldungeon.features.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.features.catalogs.dto.CreateCatalogValueRequest;
import com.centraldungeon.features.catalogs.dto.DefineTagRequest;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tags catalog. Same surface as {@link SystemController} - see it for why this is a concrete
 * class - plus the one operation only tags have: saying what a tag group is.
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    /** The only collaborator: a controller never reaches a repository. */
    private final TagService tagService;

    /**
     * @param tagService the service that owns every rule of this catalog
     */
    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * Accepted values only: what the wizard's combobox offers and what a filter can use.
     *
     * @param query    the search box, or null for the whole catalog
     * @param pageable page, size and sort; by name, with a tie-break by id
     * @return 200 with one page of accepted tags
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CatalogValueResponse> list(
            @RequestParam(name = "q", required = false) @Nullable String query,
            @PageableDefault(size = 20, sort = {"name", "id"}) Pageable pageable) {
        return tagService.search(query, pageable);
    }

    /**
     * Whoever writes a table proposes - a Master, an admin, or somebody assigned to run one;
     * only an admin accepts and classifies.
     *
     * @param request     the name to add to the catalog
     * @param currentUser the proposer, from the token
     * @return 201 with the created tag, in {@code Created}. 409 if the name
     *         is already taken, 403 if the proposer writes no table
     */
    @PostMapping
    // Authenticated here, decided in the service: proposing is membership as well as role.
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CatalogValueResponse> propose(
            @Valid @RequestBody CreateCatalogValueRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        CatalogValueResponse created = tagService.propose(request.name(), currentUser.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * An admin sets a tag group's category and definition. Both replace what the group had; sending
     * null clears them.
     *
     * @param id      the group's canonical entry
     * @param request the category and the definition
     * @return 200 with the tag as the admin screen sees it. 404 if no tag has that id, 409
     *         {@code CATALOG_VALUE_NOT_CANONICAL} if it is an alias, 409 if it was rejected
     */
    @PutMapping("/{id}/definition")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminCatalogValueResponse define(@PathVariable String id, @Valid @RequestBody DefineTagRequest request) {
        return tagService.define(id, request.category(), request.description());
    }
}
