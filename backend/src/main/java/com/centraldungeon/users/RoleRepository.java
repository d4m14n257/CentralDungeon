package com.centraldungeon.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads the {@code roles} table. Fixed reference data - the four rows come from V2__seed.sql. */
public interface RoleRepository extends JpaRepository<Role, String> {

    /**
     * Looks a role up by name, which is how the code refers to them ({@link PlatformRole}).
     *
     * @param name the role name, exactly as {@code roles.name} spells it
     * @return the role, or empty if the seed never created it
     */
    Optional<Role> findByName(String name);
}
