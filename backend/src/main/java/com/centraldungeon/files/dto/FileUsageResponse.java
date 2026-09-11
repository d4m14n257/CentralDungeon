package com.centraldungeon.files.dto;

/**
 * One place a file is being used, as its owner sees it in their own list (#232).
 *
 * <p>It is what turns the reuse history of #65 from a list of filenames into something a person
 * recognises: "the sheet I use on Hijos del Vacío" rather than "ficha-thalia-n5.pdf". It is also how
 * the owner can tell what the purge of #75 would reclaim - a file with no uses at all is the one
 * about to go quiet.
 *
 * <p><b>A use is not a cajón, though it names one.</b> A cajón says the file has belonged to a flow
 * and is never revoked; this says it is linked somewhere right now, and vanishes when the link does.
 *
 * <p>The enum leaves as a string, like every other one on this boundary (arquitectura.md 2.3).
 *
 * @param category    the cajón this use puts the file in (#233), as a string
 * @param contextId   the table the use belongs to, so the screen can link to it
 * @param contextName the table's name, which is what the reader actually recognises
 */
public record FileUsageResponse(String category, String contextId, String contextName) {
}
