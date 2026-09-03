package com.centraldungeon.users;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes {@code users_roles} - who currently holds which global role. */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

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
