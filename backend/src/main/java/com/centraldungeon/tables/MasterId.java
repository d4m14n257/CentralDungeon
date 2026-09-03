package com.centraldungeon.tables;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link Master}: one person runs a given table once.
 *
 * @param gameTableId the table
 * @param userId      the person running it
 */
@Embeddable
public record MasterId(String gameTableId, String userId) implements Serializable {
}
