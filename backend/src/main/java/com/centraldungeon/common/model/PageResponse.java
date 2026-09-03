package com.centraldungeon.common.model;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The envelope every paginated endpoint answers with (arquitectura.md 2.5). It exists so Spring
 * Data's {@code Page} never crosses HTTP: that type carries the {@code Pageable} and the {@code Sort}
 * with it, which is internal machinery the frontend has no reason to receive or to depend on.
 *
 * <p>It lives in {@code common/model} and not in any feature's {@code dto/} because more than one
 * feature uses it - today the only DTO that qualifies (arquitectura.md 2.3).
 *
 * @param <T>           the item type of this page
 * @param content       the page's items, in order. Never null; an empty page is an empty list
 * @param page          the zero-based page number that was served
 * @param size          the page size that was applied, capped at 100 (#173)
 * @param totalElements how many items match the query across every page
 * @param totalPages    how many pages that adds up to at the current size
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * Wraps a Spring Data page, dropping everything the client does not need.
     *
     * @param page the page to convert, already mapped to its response type
     * @param <T>  the item type
     * @return the same page as an API envelope
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
