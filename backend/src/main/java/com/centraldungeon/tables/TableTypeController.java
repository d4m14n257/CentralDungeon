package com.centraldungeon.tables;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.tables.dto.TableTypeResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The gap F1 opened with: V2__seed.sql has been seeding table types since E1 and nothing could list
 * them, so the wizard had no way to offer the field.
 *
 * <p>Paginated like every other collection (arquitectura.md 2.5), even though there are two rows
 * today: it is reference data an admin will eventually extend, and an endpoint that answers with a
 * bare list is one that has to change shape the day it grows.
 */
@RestController
@RequestMapping("/api/v1/table-types")
public class TableTypeController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final TableTypeService tableTypeService;

    /**
     * @param tableTypeService the service that reads the table types
     */
    public TableTypeController(TableTypeService tableTypeService) {
        this.tableTypeService = tableTypeService;
    }

    /**
     * Every table type. Authenticated rather than public: it is only useful to someone already
     * inside, and a catalog of the community's table formats is not something to open wider than its
     * consumers need.
     *
     * @param pageable page, size and sort; by name with a tie-break by id (#171). The default size
     *                 is 50 because the whole list is meant to arrive in one page
     * @return 200 with one page of table types
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<TableTypeResponse> list(@PageableDefault(size = 50, sort = {"name", "id"}) Pageable pageable) {
        return tableTypeService.list(pageable);
    }
}
