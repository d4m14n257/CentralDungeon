package com.centraldungeon.tables;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record MasterId(String gameTableId, String userId) implements Serializable {
}
