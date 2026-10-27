package com.centraldungeon.notifications;

/**
 * How the recipient of a table-change notification is joined to that table.
 *
 * <p>The same notification type reaches the masters and the players of a table, but the two land on
 * different screens when they click it: a master reads the reason in the table's history, a player
 * opens the table they play at. The type cannot say which one the reader is, so this travels with it.
 */
public enum TableMembership {

    /** The recipient is a live master of the table, Primary or Secondary. */
    Master,

    /** The recipient is an accepted player of the table. */
    Player
}
