package com.centraldungeon.catalogs;

import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.catalogs.dto.CreateCatalogValueRequest;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The tags catalog. Same surface as {@link SystemController}; see it for why this is a concrete class. */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final TagService tagService;

    /**
     * @param tagService the service that owns every rule of this catalog
     */
    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * Accepted values only (ADR-12 §3): what the wizard's combobox offers and what a filter can use.
     *
     * @param query    the search box, or null for the whole catalog
     * @param pageable page, size and sort; by name, with a tie-break by id (ADR-07 §9)
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
     * One tag, whatever its status, so a pending proposal can be shown as pending (ADR-12 §3).
     *
     * @param id the tag to read
     * @return 200 with the tag, or 404 if it does not exist
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CatalogValueResponse find(@PathVariable String id) {
        return tagService.find(id);
    }

    /**
     * Whoever writes a table proposes - a Master, an admin, or somebody assigned to run one (ADR-06 §4);
     * only an admin accepts and classifies (ADR-12 §3).
     *
     * @param request     the name to add to the catalog
     * @param currentUser the proposer, from the token (ADR-05 §5)
     * @return 201 with the created tag, in {@code Created}, and its Location header. 409 if the name
     *         is already taken, 403 if the proposer writes no table
     */
    @PostMapping
    // Authenticated here, decided in the service: proposing is pertenencia as well as role (ADR-06 §4).
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CatalogValueResponse> propose(
            @Valid @RequestBody CreateCatalogValueRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        CatalogValueResponse created = tagService.propose(request.name(), currentUser.userId());
        return ResponseEntity.created(URI.create("/api/v1/tags/" + created.id())).body(created);
    }
}
