package com.centraldungeon.users;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * The same answer as {@link #findActiveRoleNames}, for a whole page of people at once. What
     * {@code /admin/users} lists its rows with: twenty people cost one query rather than twenty.
     *
     * @param userIds the people on the page. An empty collection is not passed - the caller skips
     *                the query instead, since {@code in ()} is not valid SQL
     * @return one entry per live grant. Never null; somebody with no roles simply has no entry
     */
    @Query("""
            select new com.centraldungeon.users.UserRoleGrant(ur.user.id, ur.role.name) from UserRole ur
            where ur.user.id in :userIds and ur.status = com.centraldungeon.users.UserRoleStatus.Allowed
            """)
    List<UserRoleGrant> findActiveGrantsByUsers(@Param("userIds") Collection<String> userIds);

    /**
     * How many people hold a role and can actually use it. <b>An observation, not a decision.</b>
     *
     * <p>"Can actually use it" is the whole point when the role is Owner: the platform is never left
     * without one (fase-3-admin-owner.md 3), and an owner whose account is Blocked or Deleted cannot
     * log in to grant the role to anybody, so counting them would satisfy the invariant on paper
     * while breaking it in fact.
     *
     * <p><b>Do not enforce the invariant with this one.</b> It is a plain read, so under MySQL's
     * REPEATABLE READ it answers from the transaction's snapshot - which is exactly how two
     * concurrent revocations each saw a colleague who was already gone and left the platform with
     * zero owners. {@link #lockActiveHolders} is the one that decides; this one is for reporting and
     * for a test asserting the outcome from outside any transaction.
     *
     * @param roleName the role, as {@code roles.name} spells it
     * @return how many Allowed accounts hold a live grant of it
     */
    @Query("""
            select count(ur) from UserRole ur
            where ur.role.name = :roleName
              and ur.status = com.centraldungeon.users.UserRoleStatus.Allowed
              and ur.user.status = com.centraldungeon.users.UserStatus.Allowed
            """)
    long countActiveHolders(@Param("roleName") String roleName);

    /**
     * The same question as {@link #countActiveHolders}, asked in a way that can be trusted to decide
     * with: a locking read.
     *
     * <p>Two properties, and the invariant needs both. It <b>serialises</b> - a second transaction
     * reaching the same rows waits instead of racing. And it is <b>fresh</b>: a locking read in
     * MySQL bypasses the transaction's snapshot and sees the latest committed rows, which a plain
     * {@code count(*)} does not. That second half is the subtle one. Taking a lock and then counting
     * with a plain query looks right and is not: the waiting transaction wakes up and re-reads its
     * own stale snapshot, counts the owner that no longer exists, and proceeds.
     *
     * <p>Returns the rows rather than a number because a lock mode on an aggregate query has nothing
     * to attach to. There are never many owners, so the list is the count.
     *
     * @param roleName the role, as {@code roles.name} spells it
     * @return the live grants of that role held by usable accounts, locked for the rest of the
     *         transaction. Never null
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ur from UserRole ur
            where ur.role.name = :roleName
              and ur.status = com.centraldungeon.users.UserRoleStatus.Allowed
              and ur.user.status = com.centraldungeon.users.UserStatus.Allowed
            """)
    List<UserRole> lockActiveHolders(@Param("roleName") String roleName);
}
