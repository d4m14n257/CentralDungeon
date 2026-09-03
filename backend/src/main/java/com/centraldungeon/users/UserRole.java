package com.centraldungeon.users;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A role granted to a person. Table {@code users_roles}.
 *
 * <p>Roles stack and have no hierarchy (#37, #67): someone can hold Player and Master at once, or
 * Master without Player. The only pair that never coexists is Admin and Owner, which are the same
 * role at two scopes (#169). Nothing here enforces that - {@code UserService} does.
 *
 * <p>Revoking a role marks the row rather than deleting it, so the record of who once held what
 * survives (#25).
 */
@Entity
@Table(name = "users_roles")
public class UserRole {

    /** The pair (user, role) this grant joins. */
    @EmbeddedId
    private UserRoleId id;

    /** Who holds the role. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    /** Which role they hold. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    /** Whether the grant still counts. Read on every request, never from a token (#122). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRoleStatus status = UserRoleStatus.Allowed;

    /** When the role was granted. Stamped on persist. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** When it was revoked, or null while the grant is live. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected UserRole() {
    }

    /**
     * Grants a role to someone.
     *
     * @param user the person, already persisted
     * @param role the role to grant
     */
    public UserRole(User user, Role role) {
        this.user = user;
        this.role = role;
        this.id = new UserRoleId(user.getId(), role.getId());
    }

    /** Stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Returns who holds the role.
     *
     * @return the user, lazily loaded
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns which role they hold.
     *
     * @return the role, lazily loaded
     */
    public Role getRole() {
        return role;
    }

    /**
     * Returns whether the grant still counts.
     *
     * @return the status, never null
     */
    public UserRoleStatus getStatus() {
        return status;
    }

    /**
     * Revokes or restores the grant.
     *
     * @param status the new status. Changing it has to evict the security cache, or the old roles
     *               stay live for up to its TTL (#128)
     */
    public void setStatus(UserRoleStatus status) {
        this.status = status;
    }
}
