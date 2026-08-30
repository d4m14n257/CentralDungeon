package com.centraldungeon.users;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("""
            select ur.role.name from UserRole ur
            where ur.user.id = :userId and ur.status = com.centraldungeon.users.UserRoleStatus.Allowed
            """)
    Set<String> findActiveRoleNames(@Param("userId") String userId);
}
