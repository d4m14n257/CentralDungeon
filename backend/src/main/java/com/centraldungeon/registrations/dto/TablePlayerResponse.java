package com.centraldungeon.registrations.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One person on a table's roster, for the places that need it.
 *
 * <p>It exists because until F1.5 nothing could list them: {@code GET /game-tables/{id}/registrations}
 * answers with the <b>candidate</b> queue (#28), which is a different question. The first caller is
 * the picker that a {@code Single} task needs - addressing one player means being able to choose
 * among them, and offering the platform's whole user directory there would be offering people who
 * cannot be chosen.
 *
 * <p><b>F3.4 added the veto to it</b> (#39). The roster now carries {@code registrationId} and
 * {@code status}, and a vetoed row stays on the list rather than vanishing from it: a veto that
 * disappears from the screen is not reversible in practice, and the button that lifts it has to sit
 * somewhere. The three {@code blocked*} fields are what the master reads before deciding to lift it.
 *
 * <p>A vetoed person is still impossible to address a {@code Single} task to - that check asks for a
 * live {@code Player} registration in {@code TableTaskService}, and {@code Blocked} is not one. The
 * picker and the players tab read the same roster and the status is what tells them apart.
 *
 * <p>Deliberately smaller than {@link RegistrationResponse}: this is a roster, not an application.
 * When they applied and what they wrote is the candidate queue's business.
 *
 * @param registrationId     the application this row is, which is what the veto endpoints address
 * @param userId             the person
 * @param userName           how to name them on screen - their Discord username, which everybody has
 * @param userKarma          their karma, which is what a master weighs people by elsewhere
 * @param status             {@code Player} or {@code Blocked}, as a string. Nothing else reaches
 *                           this list
 * @param blockedByName      who vetoed them, or null when they are not vetoed (#39)
 * @param blockedAt          when, or null when they are not vetoed
 * @param blockJustification the reason that was written down, or null when they are not vetoed.
 *                           <b>None of the three ever reaches the vetoed person</b>: the table
 *                           answers them 404 (#29), so the only readers are its masters
 */
public record TablePlayerResponse(
        String registrationId,
        String userId,
        String userName,
        int userKarma,
        String status,
        @Nullable String blockedByName,
        @Nullable LocalDateTime blockedAt,
        @Nullable String blockJustification) {
}
