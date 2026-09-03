package com.centraldungeon.tables.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of every transition that denies something: cancel, request changes, reject.
 *
 * @param justification why. Required, and deliberately so: a table that comes back to its master
 *                      without a reason is a dead end, and the reason is what the status history
 *                      keeps (decisiones.md, ciclo de vida de la mesa)
 */
public record ChangeTableStatusRequest(@NotBlank String justification) {
}
