package com.centraldungeon.users;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes {@code users_roles} - who currently holds which global role. */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    /**
     * Every grant the person has ever had, revoked ones included - what a caller needs in order to
     * change what they hold. A revoked row is restored by flipping its status rather than inserting a
     * second one: {@code (user_id, role_id)} is the primary key, so there is only ever one row per
     * pair. {@link #findActiveRoleNames} answers the authorization question and is the one on the hot
     * path; this one is for the rare write.
     *
     * @param userId the person
     * @return their rows of {@code users_roles}, whatever their status. Never null
     */
    @Query("select ur from UserRole ur where ur.user.id = :userId")
    List<UserRole> findAllGrants(@Param("userId") String userId);

    /**
     * The roles a person holds right now. Called on every authenticated request by
     * {@code JwtAuthenticationFilter}, which is the whole point of #122: authorization is read from
     * the database, not lifted from the token's claims. The cost is absorbed by the Caffeine cache
     * (#128), whose TTL is the revocation window.
     *
     * @param userId the person, resolved from the token's subject
     * @return their live role names. Never null; someone with no roles gets an empty set
     */
    @Query("""
            select ur.role.name from UserRole ur
            where ur.user.id = :userId and ur.status = com.centraldungeon.users.UserRoleStatus.Allowed
            """)
    Set<String> findActiveRoleNames(@Param("userId") String userId);
}
