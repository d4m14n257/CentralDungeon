package com.centraldungeon.users;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes {@code user_role_changes} - the trail of who moved whose roles, and why. */
public interface UserRoleChangeRepository extends JpaRepository<UserRoleChange, String> {

    /**
     * Everything that ever happened to one person's roles, oldest first - the order the history
     * panel of {@code /admin/users} reads it in, matching {@code table_status_changes}.
     *
     * <p>{@code changedBy} and {@code role} are fetched with the rows and not lazily: the panel
     * renders the actor's <em>name</em> and the role's name on every line, so leaving them lazy
     * turns one history into one query per entry.
     *
     * @param userId the person
     * @return their role changes in chronological order. Never null, possibly empty
     */
    @EntityGraph(attributePaths = {"changedBy", "role"})
    List<UserRoleChange> findByUser_IdOrderByCreatedAtAsc(String userId);
}
