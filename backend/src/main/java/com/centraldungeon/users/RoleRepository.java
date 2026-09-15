package com.centraldungeon.users;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads the {@code roles} table. Fixed reference data - the four rows come from V2__seed.sql. */
public interface RoleRepository extends JpaRepository<Role, String> {

    /**
     * Looks a role up by name, which is how the code refers to them ({@link PlatformRole}).
     *
     * @param name the role name, exactly as {@code roles.name} spells it
     * @return the role, or empty if the seed never created it
     */
    Optional<Role> findByName(String name);

    /**
     * The same lookup, taking a write lock on the row. <b>The mutex of the Owner invariant.</b>
     *
     * <p>A four-row reference table is an odd place for a lock until you see what it buys: it is a
     * single, always-present row that every operation able to remove an Owner can agree to take
     * <em>first</em>. That gives a total order over rank mutations with no possibility of a cycle -
     * there is only ever one row to wait for - and it works even in the case a lock on the grants
     * themselves cannot cover, which is when there are no rows left to lock.
     *
     * <p>The blast radius is deliberately tiny: nothing else in the application locks this table, and
     * granting Player or Master never comes here. Only the rank moves, which are rare.
     *
     * @param name the role to lock, as {@code roles.name} spells it
     * @return the role, or empty if the seed never created it
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.name = :name")
    Optional<Role> lockByName(@Param("name") String name);
}
