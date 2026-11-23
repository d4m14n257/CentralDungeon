package com.centraldungeon.features.registrations;

/**
 * Who an application is from and which table it was made to. Internal projection of
 * {@link TableRegistrationRepository}, not a DTO: it never crosses HTTP as itself.
 *
 * <p>It exists so a page of veto requests - in the admin tray, in the request listing or on the
 * table's roster - can name the person each one is about and link its table with one query for the
 * whole page, instead of loading every application and its user.
 *
 * @param registrationId  the application
 * @param gameTableId     the table it was made to
 * @param applicantUserId the applicant's user id
 * @param applicantName   the applicant's display name: their name, or their Discord username when they
 *                        have none
 */
public record RegistrationSubject(
        String registrationId, String gameTableId, String applicantUserId, String applicantName) {
}
