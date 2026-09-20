package com.centraldungeon.registrations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Vetoing somebody from a table, or asking the {@code Primary} to (#39).
 *
 * <p><b>One record for the two doors</b>, and that is not a shortcut: what a {@code Primary} writes
 * when they veto and what a {@code Secondary} writes when they ask for one are the same sentence -
 * the reason. The two endpoints differ in who may call them and in what comes back, which is why
 * they are two (#4 and #6 of the contract), not in what goes in.
 *
 * @param justification why. Never blank: #39 asks that a veto be reversible, and a veto with no
 *                      reason is one nobody - the {@code Primary} included, six months later - can
 *                      decide whether to lift. It is also the text a co-master's request is read on
 */
public record BlockRegistrationRequest(@NotBlank @Size(max = 4000) String justification) {
}
