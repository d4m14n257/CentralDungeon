package com.centraldungeon.registrations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lifting a veto (#39).
 *
 * <p><b>A record of its own rather than reusing {@link BlockRegistrationRequest}</b>, even though
 * the two carry one identical field. They are different acts and the shapes are free to diverge -
 * the same reasoning that keeps a summary DTO and a detail DTO apart (arquitectura.md §2.3). Folding
 * them together would also make the endpoint pair read as one operation with a flag, which is the
 * shape #39's reversibility is specifically not.
 *
 * @param justification why the veto is being lifted. Never blank, and for the reason the whole
 *                      trail exists: #39 wants «que un patrón de vetos impulsivos sea visible», and
 *                      a lifting with no words is half of that pattern going unrecorded
 */
public record UnblockRegistrationRequest(@NotBlank @Size(max = 4000) String justification) {
}
