package com.centraldungeon.files;

/**
 * Who a {@link FileType#Public} file is meant for (#64).
 *
 * <p>Closes M24.1. The legacy's {@code getPublicFilesFromTable} returned every public file with no
 * notion of context, so a document written for masters showed up in front of a player. With the
 * audience declared on the file, each screen asks for its own and no query has to remember to
 * filter by hand.
 *
 * <p>Null on every file that is not {@code Public}: an audience only means something for a file the
 * platform published.
 */
public enum PublicAudience {

    /** For the people who run tables: guidance, formats, whatever a master needs to prepare. */
    Masters,

    /** For the people who play: the default character sheet is the case that motivated #79. */
    Players,

    /** For everybody: rules of the community, announcements, anything published at large. */
    Announcements
}
