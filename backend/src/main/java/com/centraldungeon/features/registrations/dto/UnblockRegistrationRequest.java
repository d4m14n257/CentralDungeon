package com.centraldungeon.features.registrations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lifting a veto.
 *
 * <p><b>A record of its own rather than reusing {@link BlockRegistrationRequest}</b>, even though
 * the two carry one identical field. They are different acts and the shapes are free to diverge -
 * the same reasoning that keeps a summary DTO and a detail DTO apart. Folding
 * them together would also make the endpoint pair read as one operation with a flag, which is the
 * shape a reversible veto is specifically not.
 *
 * @param justification why the veto is being lifted. Never blank, and for the reason the whole
 *                      trail exists: it has to make a pattern of impulsive vetoes visible, and
 *                      a lifting with no words is half of that pattern going unrecorded
 */
public record UnblockRegistrationRequest(@NotBlank @Size(max = 4000) String justification) {
}
