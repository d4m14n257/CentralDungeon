package com.centraldungeon.settings.dto;

/**
 * The limits the interface has to know <em>before</em> it lets somebody break one.
 *
 * <p><b>Why this endpoint exists at all.</b> «Los límites se dicen antes de romperlos» (principio 2
 * de frontend-diseno.md §1) is why {@code FileDropzone} refuses an oversized file on the spot
 * instead of letting somebody fill in four wizard steps first - and to do that it needs the number.
 * Until F3.5 the number was a constant in two places that happened to agree; the moment an admin can
 * change one of them without a deploy, a hardcoded mirror is a client that refuses files the server
 * would have taken, or takes files the server will refuse. The acceptance test of this slice - «un
 * admin cambia el tope por archivo; la subida siguiente lo respeta sin reiniciar nada» - is false
 * without it.
 *
 * <p><b>It is not the authority and it is not the security.</b> The server checks the same limit
 * again on upload and answers {@code FILE_TOO_LARGE} with the real number (#197); this only makes
 * the refusal immediate. It carries no setting whose value is a decision about somebody else, which
 * is why it is readable by anybody signed in and lives outside {@code /admin}.
 *
 * <p><b>Two fields, and both are rendered</b> - a response carrying a limit nothing shows would be
 * the orphan #255 warns about. {@code tables.max_players_cap} is deliberately not here: no screen
 * states it before it is broken today, and the day one does it is one field here and one in the
 * service.
 *
 * @param maxFileSizeBytes    the per-file cap in bytes, so the client never has to know which unit
 *                            the setting is stored in (#75, #141)
 * @param claimTimeoutMinutes how long a reservation of the shared tray lasts (#100). The frontend
 *                            never enforces it - the release job does - but the tray and its help
 *                            both say the number out loud, and {@code config/adminQueue.ts} wrote in
 *                            so many words that the day it stopped being a constant here it would
 *                            stop being one there and arrive from the server instead
 */
public record ClientLimitsResponse(long maxFileSizeBytes, int claimTimeoutMinutes) {
}
