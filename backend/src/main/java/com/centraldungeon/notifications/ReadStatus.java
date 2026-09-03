package com.centraldungeon.notifications;

/** Whether the recipient has seen a notification. It is what the bell's unread count is built on. */
public enum ReadStatus {

    /** Not seen yet. Counted by the bell. */
    Unread,

    /** Seen. Kept in the list, out of the count. */
    Read
}
