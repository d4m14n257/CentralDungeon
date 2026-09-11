package com.centraldungeon.files;

/**
 * The flow a file belongs to (#233) - its cajón.
 *
 * <p><b>It says where a file is used, not what the document is.</b> That distinction is the whole
 * design and the first attempt got it backwards. Nobody uploading can honestly declare "this is a
 * character sheet template": what a master asks for might be a form in a {@code .doc} or a
 * spreadsheet, and the person attaching it has no way to know what the other side will call it. What
 * the system <em>does</em> know for certain is which screen the file came from, and that is what
 * classifies it - so this is never asked, it is observed.
 *
 * <p><b>A file belongs to as many cajones as it has been used in</b>, which is why membership lives
 * in {@code file_categories} and not in a column. A sheet uploaded on an application and later handed
 * in to a task belongs to both, and deduplication (#75) makes that the normal case rather than an
 * edge one: the same bytes are one row by design, so one row has to be able to hold two answers.
 *
 * <p><b>Membership is never revoked.</b> Detaching a file from a table says nothing about whether it
 * was ever that table's material - it was. The bridge tables track what is linked now; this tracks
 * what a file has been.
 *
 * <p>The order below is the order a table lives in: assembled, recruited for, played, and the one
 * that belongs to no table at all.
 */
public enum FileCategory {

    /**
     * What a master attaches to their table: maps, handouts, house rules, the sheet they require.
     *
     * <p>Filled while the table is being assembled and while it recruits. An admin publishes here so
     * that the community's blank sheet is attached rather than re-uploaded by every master (#79).
     */
    TableMaterial(true),

    /**
     * What a master attaches when <b>asking</b> for something on a table already in progress - the
     * blank the request is about (#63).
     *
     * <p>A different moment from {@link #TableMaterial} and therefore a different cajón: one is what
     * the table offers, the other is what it asks for halfway through. The same published document
     * can sit in both, which costs two rows and no duplication - that is what a relation buys.
     */
    MasterRequest(true),

    /**
     * What a player uploads when applying to a table (#60 uso 2).
     *
     * <p><b>An admin cannot publish here.</b> This cajón holds what each person answered with; a
     * blank offered to everybody is not an answer, and it belongs in the master-side cajón the
     * request came from.
     */
    PlayerApplication(false),

    /**
     * What a player hands in answering a request on a table they already play at (#63, #76).
     *
     * <p>Not publishable, for the same reason as {@link #PlayerApplication}.
     */
    PlayerSubmission(false),

    /**
     * What the community publishes at large: its rules, an announcement.
     *
     * <p><b>The one cajón that is not a flow</b>, and the reason it exists is that dropping
     * {@code public_audience} would otherwise leave this kind of document homeless: it comes out of
     * no application and is used on no table. Only an admin fills it, and everything in it is
     * published by construction.
     */
    Announcement(true);

    /** Whether an admin may publish a file into this cajón. */
    private final boolean publishable;

    /**
     * @param publishable whether an admin may publish into this cajón
     */
    FileCategory(boolean publishable) {
        this.publishable = publishable;
    }

    /**
     * Returns whether an admin may publish a file into this cajón.
     *
     * <p>The two player-side cajones say no. They hold what individual people answered with, and a
     * blank offered to the whole community is not an answer - it is the request's, and it belongs in
     * the master-side cajón the request was written from.
     *
     * @return true when publishing here is allowed
     */
    public boolean isPublishable() {
        return publishable;
    }
}
