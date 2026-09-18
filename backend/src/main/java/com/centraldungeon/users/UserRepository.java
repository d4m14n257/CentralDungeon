package com.centraldungeon.users;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JpaSpecificationExecutor is what the search box needs: its predicate is built at runtime (UserSearchSpecification). */
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

    Optional<User> findByDiscordId(String discordId);

    /**
     * One person, read <b>with their row locked</b> for the rest of the transaction.
     *
     * <p>A serialization point, not a load. It exists for the rules that are "one of these per
     * person" and that no index can express - {@code ApprovalService.submit} is the first: "one
     * Pending request per type and per person" needs a filtered unique key, which MySQL does not
     * have, so the only thing left is to make two submits by the same person take turns.
     *
     * <p><b>A single, always-present row</b>, which is what makes it safe. It is the same shape
     * {@code RoleRepository.lockByName} has in the last-owner invariant (#252), and for the same
     * reason: one row means no lock cycle is even expressible, and it covers the case a locking read
     * over the rows being counted cannot - when there are no rows yet, which is exactly the state an
     * insert starts from. Locking an empty result set in InnoDB means gap locks, whose reach depends
     * on which index the planner picked; a single primary-key row has no such ambiguity.
     *
     * @param userId the person to lock
     * @return the person, locked, or empty when nobody has that id
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> lockById(@Param("userId") String userId);
}
