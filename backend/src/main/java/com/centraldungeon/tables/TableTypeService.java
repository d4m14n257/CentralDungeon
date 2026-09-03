package com.centraldungeon.tables;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.tables.dto.TableTypeResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the table types. Trivial, and it exists anyway because a controller never calls a repository
 * (regla dura 1) - not even for a list of two rows.
 */
@Service
public class TableTypeService {

    /** The {@code table_types} table. Read-only: the rows come from V2__seed.sql. */
    private final TableTypeRepository tableTypeRepository;

    /**
     * @param tableTypeRepository the {@code table_types} table
     */
    public TableTypeService(TableTypeRepository tableTypeRepository) {
        this.tableTypeRepository = tableTypeRepository;
    }

    /**
     * Every table type, for the wizard's selector.
     *
     * @param pageable page, size and sort; callers order by name with a tie-break by id (#171)
     * @return one page of types
     */
    @Transactional(readOnly = true)
    public PageResponse<TableTypeResponse> list(Pageable pageable) {
        return PageResponse.from(tableTypeRepository
                .findAll(pageable)
                .map(type -> new TableTypeResponse(type.getId(), type.getName(), type.getDescription())));
    }
}
