package com.centraldungeon.registrations;

/**
 * Which table an application belongs to. Internal projection of {@link TableRegistrationRepository},
 * not a DTO: it never crosses HTTP.
 *
 * <p>It exists so a page of requests can say which table each veto is about with one query for the
 * whole page, instead of loading every application to read a foreign key off it.
 *
 * @param registrationId the application
 * @param gameTableId    the table it was made to
 */
public record RegistrationTable(String registrationId, String gameTableId) {
}
