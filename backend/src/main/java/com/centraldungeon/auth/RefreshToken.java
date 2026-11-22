package com.centraldungeon.auth;

import com.centraldungeon.common.model.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One refresh token the server handed out. Table {@code refresh_tokens}.
 *
 * <p>The server remembers the tokens so it can end a session: a signed token alone stays valid until it
 * expires, whatever happens. Every token of one login shares a family, and renewing replaces one token
 * of the family with the next.
 *
 * <p>Only a hash of the token's id is stored, never the token, so reading this table gives nobody a
 * session. The person is kept as a plain id rather than a relation: nothing here ever needs the user
 * loaded, only matched.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    /** Row id, assigned on persist. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** Hex SHA-256 of the token's {@code jti}: how a presented token finds its row. */
    @Column(name = "jti_hash", nullable = false, length = 64, unique = true)
    private String jtiHash;

    /** Shared by every token descended from the same login. Revoking the family ends that session. */
    @Column(name = "family_id", nullable = false, length = 64)
    private String familyId;

    /** Whose session this is. */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** When the token expires, the same instant as its own {@code exp}. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** When it stopped being usable - rotated, reused or logged out. Null while it is the live one. */
    @Column(name = "revoked_at")
    private @Nullable LocalDateTime revokedAt;

    /** When it was issued. Stamped on persist. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Required by JPA. */
    protected RefreshToken() {
    }

    /**
     * Records a freshly issued token.
     *
     * @param jtiHash   hex SHA-256 of the token's {@code jti}
     * @param familyId  the login it descends from
     * @param userId    whose session it is
     * @param expiresAt when the token expires
     */
    public RefreshToken(String jtiHash, String familyId, String userId, LocalDateTime expiresAt) {
        this.jtiHash = jtiHash;
        this.familyId = familyId;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    /** Assigns the id and stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
        createdAt = LocalDateTime.now();
    }

    /**
     * Returns the family the token belongs to.
     *
     * @return the family id, shared by every token of the same login
     */
    public String getFamilyId() {
        return familyId;
    }

    /**
     * Returns whose session this is.
     *
     * @return the user id
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Whether the token was already spent or revoked.
     *
     * @return true once {@code revokedAt} is set
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Whether the token stopped being usable after a given moment.
     *
     * @param moment the cut-off
     * @return true when it is revoked and {@code revokedAt} is later than {@code moment}
     */
    public boolean wasRevokedAfter(LocalDateTime moment) {
        return revokedAt != null && revokedAt.isAfter(moment);
    }

    /**
     * Spends the token. Only the first call counts.
     *
     * @param when the moment it stopped being usable
     */
    public void revoke(LocalDateTime when) {
        if (revokedAt == null) {
            revokedAt = when;
        }
    }
}
