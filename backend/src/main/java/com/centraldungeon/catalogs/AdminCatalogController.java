package com.centraldungeon.catalogs;

import com.centraldungeon.catalogs.dto.AcceptCatalogValueRequest;
import com.centraldungeon.catalogs.dto.AdminCatalogValueResponse;
import com.centraldungeon.catalogs.dto.DisableCatalogValueRequest;
import com.centraldungeon.catalogs.dto.MergeCatalogGroupsRequest;
import com.centraldungeon.catalogs.dto.SplitCatalogGroupRequest;
import com.centraldungeon.common.model.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /admin/catalogs: the six operations that make the synonym groups an admin's job (#55, #179).
 *
 * <p>One controller for the three catalogs, with the catalog as a typed path variable, because the
 * operations are identical on all three and writing them out three times would mean fixing every bug
 * three times. What is <b>not</b> shared is the authorization: every method below declares its own
 * {@code @PreAuthorize}, none of it comes from a base class or a route list (#123).
 *
 * <p>Admin and Owner are enumerated explicitly on each one. There is no {@code RoleHierarchy} in
 * this project: Owner can do everything Admin can by being listed, not by inheriting (#169).
 */
@RestController
@RequestMapping("/api/v1/admin/catalogs/{type}")
public class AdminCatalogController {

    /** Resolves the {@code {type}} path variable to the service that handles that catalog. */
    private final CatalogServices catalogServices;

    /**
     * @param catalogServices the registry of the three catalog services
     */
    public AdminCatalogController(CatalogServices catalogServices) {
        this.catalogServices = catalogServices;
    }

    /**
     * Everything, whatever its status - reviewing what was proposed is the point of this screen.
     * {@code ?status=} narrows it; with nothing, the admin sees the whole catalog.
     *
     * @param type     which catalog, from the path
     * @param query    the search box, or null for everything
     * @param statuses the statuses to keep, or null for no status filter
     * @param pageable page, size and sort; by name, with a tie-break by id (#171)
     * @return 200 with one page of values, each with its group and its usage count
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<AdminCatalogValueResponse> list(
            @PathVariable CatalogType type,
            @RequestParam(name = "q", required = false) @Nullable String query,
            @RequestParam(name = "status", required = false) @Nullable List<CatalogStatus> statuses,
            @PageableDefault(size = 20, sort = {"name", "id"}) Pageable pageable) {
        return catalogServices.of(type).adminSearch(query, statuses == null ? List.of() : statuses, pageable);
    }

    /**
     * One value's whole synonym group, canonical entry first.
     *
     * <p>What the screen needs before it can offer two of the six operations: it is where the merge
     * dialog reads the groups from, and where disabling a canonical entry finds the candidates that
     * could take it over (#59).
     *
     * @param type which catalog, from the path
     * @param id   any member of the group
     * @return 200 with the group. Not paginated - depth is 1, so a group is bounded and read whole
     */
    @GetMapping("/{id}/group")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public List<AdminCatalogValueResponse> group(@PathVariable CatalogType type, @PathVariable String id) {
        return catalogServices.of(type).group(id);
    }

    /**
     * Accept a proposal and classify it in one step: new canonical entry, or alias of a group (#55).
     *
     * @param type    which catalog, from the path
     * @param id      the proposal to accept
     * @param request the group to join, or an empty {@code canonicalId} for a group of its own
     * @return 200 with the accepted value. 404 if it or the target is unknown, 409 if the value is
     *         not pending or the target is not an accepted canonical entry
     */
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminCatalogValueResponse accept(
            @PathVariable CatalogType type, @PathVariable String id, @Valid @RequestBody AcceptCatalogValueRequest request) {
        return catalogServices.of(type).accept(id, request.canonicalId());
    }

    /**
     * A rejected value never shows and never filters (#57).
     *
     * @param type which catalog, from the path
     * @param id   the proposal to turn down
     * @return 200 with the rejected value. 409 if it was already in circulation - that is
     *         {@link #disable}, not this
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminCatalogValueResponse reject(@PathVariable CatalogType type, @PathVariable String id) {
        return catalogServices.of(type).reject(id);
    }

    /**
     * Merge two groups.
     *
     * @param type    which catalog, from the path
     * @param request the group that stops being one, and the one that survives
     * @return 200 with the surviving group's canonical entry - the row the screen has to refresh,
     *         since the source stops being one. 409 if either side is an alias or is not accepted
     */
    @PostMapping("/merge")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminCatalogValueResponse merge(@PathVariable CatalogType type, @Valid @RequestBody MergeCatalogGroupsRequest request) {
        return catalogServices.of(type).merge(request.sourceCanonicalId(), request.targetCanonicalId());
    }

    /**
     * Take one alias out of its group; it becomes a canonical entry of its own.
     *
     * @param type    which catalog, from the path
     * @param request the alias that leaves
     * @return 200 with the value, now canonical. 409 if it was already a canonical entry
     */
    @PostMapping("/split")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminCatalogValueResponse split(@PathVariable CatalogType type, @Valid @RequestBody SplitCatalogGroupRequest request) {
        return catalogServices.of(type).split(request.memberId());
    }

    /**
     * Out of circulation, without breaking a single link (#81).
     *
     * @param type    which catalog, from the path
     * @param id      the value to disable
     * @param request the successor, needed only when the value is a canonical entry with live
     *                aliases (#59)
     * @return 200 with the disabled value. 409 if a successor is needed and none was given, or the
     *         one given is not a live member of the group
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminCatalogValueResponse disable(
            @PathVariable CatalogType type, @PathVariable String id, @Valid @RequestBody DisableCatalogValueRequest request) {
        return catalogServices.of(type).disable(id, request.newCanonicalId());
    }

    /**
     * Back in circulation, in the group it was in (#81).
     *
     * @param type which catalog, from the path
     * @param id   the value to bring back
     * @return 200 with the restored value. 409 if it was not disabled
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminCatalogValueResponse restore(@PathVariable CatalogType type, @PathVariable String id) {
        return catalogServices.of(type).restore(id);
    }
}
