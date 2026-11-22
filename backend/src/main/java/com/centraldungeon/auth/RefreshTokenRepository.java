package com.centraldungeon.auth;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes {@code refresh_tokens} - the refresh tokens the server handed out. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * Finds the row of a presented token and locks it until the transaction ends, so two renewals with
     * the same token run one after the other instead of both spending it.
     *
     * @param jtiHash hex SHA-256 of the presented token's {@code jti}
     * @return the row, or empty when the server never issued that token
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.jtiHash = :jtiHash")
    Optional<RefreshToken> findByJtiHashForUpdate(@Param("jtiHash") String jtiHash);

    /**
     * Finds the row of a presented token without locking it.
     *
     * @param jtiHash hex SHA-256 of the token's {@code jti}
     * @return the row, or empty when the server never issued that token
     */
    Optional<RefreshToken> findByJtiHash(String jtiHash);

    /**
     * Whether a family still has a token that can renew: neither revoked nor expired. Logging out and a
     * reuse revoke the whole family, so a family with a live token was only ever rotated.
     *
     * @param familyId the family
     * @param now      tokens expiring at or before this instant do not count
     * @return true when at least one token of the family is live
     */
    boolean existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(String familyId, LocalDateTime now);

    /**
     * Revokes every still-live token of a family, which ends that session everywhere it is held.
     *
     * @param familyId the family to end
     * @param when     the revocation timestamp
     * @return how many tokens were revoked
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken rt set rt.revokedAt = :when where rt.familyId = :familyId and rt.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("when") LocalDateTime when);

    /**
     * Deletes the rows that can no longer matter: anything that expired before {@code expiredBefore},
     * and anything revoked whose own expiry has already passed.
     *
     * @param expiredBefore rows expired before this instant go regardless of being revoked
     * @param now           revoked rows that expired before this instant go too
     * @return how many rows were deleted
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken rt where rt.expiresAt < :expiredBefore "
            + "or (rt.revokedAt is not null and rt.expiresAt < :now)")
    int deleteStale(@Param("expiredBefore") LocalDateTime expiredBefore, @Param("now") LocalDateTime now);
}
