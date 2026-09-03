package com.centraldungeon.users;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link UserRole}: a person holds a given role once.
 *
 * @param userId the person
 * @param roleId the role they hold
 */
@Embeddable
public record UserRoleId(String userId, String roleId) implements Serializable {
}
