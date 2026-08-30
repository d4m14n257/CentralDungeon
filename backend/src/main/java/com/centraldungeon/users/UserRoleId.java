package com.centraldungeon.users;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record UserRoleId(String userId, String roleId) implements Serializable {
}
