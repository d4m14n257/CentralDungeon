package com.centraldungeon.registrations;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** The veto's trail (#39). No logic and no {@code @Transactional} - the service owns both. */
public interface RegistrationStatusChangeRepository extends JpaRepository<RegistrationStatusChange, String> {

    /**
     * One application's trail, oldest first.
     *
     * @param registrationId the application
     * @return its steps, in the order they happened
     */
    List<RegistrationStatusChange> findByRegistration_IdOrderByCreatedAtAsc(String registrationId);

    /**
     * The most recent step that took an application <em>into</em> a status.
     *
     * <p>What lifting a veto reads: {@code from_status} of the last move to {@code Blocked} is where
     * the person was, and therefore where they go back to. Guessing {@code Player} instead would be
     * right most of the time and wrong for a candidate, and «most of the time» is not a rule.
     *
     * @param registrationId the application
     * @param toStatus       the status the step landed on
     * @return the latest such step, or empty when the application never went there
     */
    Optional<RegistrationStatusChange> findFirstByRegistration_IdAndToStatusOrderByCreatedAtDesc(
            String registrationId, TableRegistrationStatus toStatus);

    /**
     * Who vetoed a set of applications and when, for the listings the master reads.
     *
     * <p>The whole page in one query rather than one per row - the same N+1 the admin listing and
     * {@code listMineHistory} already avoid. Only the moves into {@code Blocked} come back, and a
     * row that was blocked twice contributes both: the caller keeps the latest.
     *
     * @param registrationIds the applications on the page
     * @param toStatus        always {@link TableRegistrationStatus#Blocked}
     * @return their steps into that status, oldest first
     */
    List<RegistrationStatusChange> findByRegistration_IdInAndToStatusOrderByCreatedAtAsc(
            List<String> registrationIds, TableRegistrationStatus toStatus);
}
