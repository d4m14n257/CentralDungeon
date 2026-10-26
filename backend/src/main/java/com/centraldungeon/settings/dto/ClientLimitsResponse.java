package com.centraldungeon.settings.dto;

/**
 * The limits the interface has to know <em>before</em> it lets somebody break one.
 *
 * <p><b>Why this endpoint exists at all.</b> «Los límites se dicen antes de romperlos» (principle 2
 * de diseno §1) is why {@code FileDropzone} refuses an oversized file on the spot
 * instead of letting somebody fill in four wizard steps first - and to do that it needs the number.
 * Until F3.5 the number was a constant in two places that happened to agree; the moment an admin can
 * change one of them without a deploy, a hardcoded mirror is a client that refuses files the server
 * would have taken, or takes files the server will refuse. The acceptance test of this slice - «un
 * admin cambia el tope por archivo; la subida siguiente lo respeta sin reiniciar nada» - is false
 * without it.
 *
 * <p><b>It is not the authority and it is not the security.</b> The server checks the same limit
 * again on upload and answers {@code FILE_TOO_LARGE} with the real number (ADR-23 §3); this only makes
 * the refusal immediate. It carries no setting whose value is a decision about somebody else, which
 * is why it is readable by anybody signed in and lives outside {@code /admin}.
 *
 * <p><b>Three fields, and all are rendered</b> - a response carrying a limit nothing shows would be
 * a limit that is sent and never read, which only drifts out of date. The seat cap is here because the table forms state it next to the
 * field instead of waiting for the server to refuse the number.
 *
 * @param maxFileSizeBytes    the per-file cap in bytes, so the client never has to know which unit
 *                            the setting is stored in (ADR-13 §3, ADR-17 §3)
 * @param claimTimeoutMinutes how long a reservation of the shared tray lasts (ADR-16 §5). The frontend
 *                            never enforces it - the release job does - but the tray and its help
 *                            both say the number out loud, and {@code config/adminQueue.ts} wrote in
 *                            so many words that the day it stopped being a constant here it would
 *                            stop being one there and arrive from the server instead
 * @param maxPlayersCap       the largest {@code max_players} a table may declare, stated
 *                            beside the seat field of the wizard and of the table edit form
 */
public record ClientLimitsResponse(long maxFileSizeBytes, int claimTimeoutMinutes, int maxPlayersCap) {
}
