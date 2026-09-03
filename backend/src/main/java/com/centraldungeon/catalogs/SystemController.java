package com.centraldungeon.catalogs;

import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.catalogs.dto.CreateCatalogValueRequest;
import com.centraldungeon.common.model.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The game systems catalog as everyone but an admin uses it: read what is accepted, propose what is
 * missing. Everything an admin does with it - accept, reject, merge, split, disable, restore - lives
 * in {@link AdminCatalogController}.
 *
 * <p>Concrete class with its own {@code @PreAuthorize} on every method, even though the logic comes
 * from a generic base (arquitectura.md 2.4, last line): reading this file has to answer who can call
 * what, and an inherited annotation is the shape CVE-2025-41248 describes (#123).
 */
@RestController
@RequestMapping("/api/v1/systems")
public class SystemController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final SystemService systemService;

    /**
     * @param systemService the service that owns every rule of this catalog
     */
    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    /**
     * Accepted values only (#57): what the wizard's combobox offers and what a filter can use.
     *
     * @param query    the search box, or null for the whole catalog
     * @param pageable page, size and sort; by name, with a tie-break by id (#171)
     * @return 200 with one page of accepted systems
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CatalogValueResponse> list(
            @RequestParam(name = "q", required = false) @Nullable String query,
            @PageableDefault(size = 20, sort = {"name", "id"}) Pageable pageable) {
        return systemService.search(query, pageable);
    }

    /**
     * One system, whatever its status, so the master who proposed it can be shown that it is still
     * pending (#57).
     *
     * @param id the system to read
     * @return 200 with the system, or 404 if it does not exist
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CatalogValueResponse find(@PathVariable String id) {
        return systemService.find(id);
    }

    /**
     * Masters and admins propose; only an admin accepts and classifies (#55).
     *
     * @param request the name to add to the catalog
     * @return 201 with the created system, in {@code Created}, and its Location header. 409 if the
     *         name is already taken
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN','OWNER')")
    public ResponseEntity<CatalogValueResponse> propose(@Valid @RequestBody CreateCatalogValueRequest request) {
        CatalogValueResponse created = systemService.propose(request.name());
        return ResponseEntity.created(URI.create("/api/v1/systems/" + created.id())).body(created);
    }
}
