package com.centraldungeon.registrations;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes {@code registration_rejections} - the reason attached to a turned-down application. */
public interface RegistrationRejectionRepository extends JpaRepository<RegistrationRejection, String> {

    /**
     * The reasons for a whole page of registrations at once, so a list of twenty costs one query
     * instead of twenty.
     *
     * @param registrationIds the registrations on the page
     * @return their rejections. Never null; registrations that were not rejected simply have none
     */
    List<RegistrationRejection> findByRegistration_IdIn(Collection<String> registrationIds);
}
