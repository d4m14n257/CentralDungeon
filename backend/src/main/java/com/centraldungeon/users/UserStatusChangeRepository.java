package com.centraldungeon.users;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes {@code user_status_changes} - the trail of who blocked whom, and why. */
public interface UserStatusChangeRepository extends JpaRepository<UserStatusChange, String> {

    /**
     * Everything that ever happened to one account's status, oldest first.
     *
     * <p>{@code changedBy} rides along: the panel shows who did it by name on every line.
     *
     * @param userId the person
     * @return their status changes in chronological order. Never null, possibly empty
     */
    @EntityGraph(attributePaths = "changedBy")
    List<UserStatusChange> findByUser_IdOrderByCreatedAtAsc(String userId);
}
