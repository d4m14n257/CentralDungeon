package com.centraldungeon.tables.dto;

import com.centraldungeon.tables.MasterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Adding a co-master to a table, or promoting one that is already there (ADR-22 §1, ADR-07 §8).
 *
 * @param userId     the person to put in charge, picked with the UserPicker. Never the actor: the
 *                   actor comes from the token (ADR-05 §5)
 * @param masterType Primary or Secondary. Asking for Primary promotes the target and demotes the
 *                   current one - MasterService keeps exactly one live Primary (ADR-06 §3). On screen
 *                   these read as "master" and "co-master", never as these words (ADR-06 §3)
 */
public record AddMasterRequest(@NotBlank String userId, @NotNull MasterType masterType) {
}
