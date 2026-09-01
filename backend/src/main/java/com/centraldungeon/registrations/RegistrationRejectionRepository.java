package com.centraldungeon.registrations;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRejectionRepository extends JpaRepository<RegistrationRejection, String> {

    List<RegistrationRejection> findByRegistration_IdIn(Collection<String> registrationIds);
}
