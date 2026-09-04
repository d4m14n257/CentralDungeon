package com.centraldungeon.registrations.dto;

/**
 * One player currently at a table, for the places that need its roster.
 *
 * <p>It exists because until F1.5 nothing could list them: {@code GET /game-tables/{id}/registrations}
 * answers with the <b>candidate</b> queue (#28), which is a different question. The first caller is
 * the picker that a {@code Single} task needs - addressing one player means being able to choose
 * among them, and offering the platform's whole user directory there would be offering people who
 * cannot be chosen.
 *
 * <p>Deliberately smaller than {@link RegistrationResponse}: this is a roster, not an application.
 * When they applied and what they wrote is the candidate queue's business.
 *
 * @param userId    the player
 * @param userName  how to name them on screen - their Discord username, which everybody has
 * @param userKarma their karma, which is what a master weighs people by elsewhere in the interface
 */
public record TablePlayerResponse(String userId, String userName, int userKarma) {
}
