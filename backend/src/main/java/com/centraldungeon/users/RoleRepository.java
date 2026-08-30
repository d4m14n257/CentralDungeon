package com.centraldungeon.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {

    Optional<Role> findByName(String name);
}
