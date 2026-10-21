package com.centraldungeon.tables;

/** Not the platform role Owner (ADR-06 §1) - Primary is this table's Owner-equivalent. */
public enum MasterType {

    /** The table's owner. Exactly one live per table (ADR-06 §3). Shown as "master" (ADR-06 §3). */
    Primary,

    /** A co-master. Any number of them. Shown as "co-master" (ADR-06 §3). */
    Secondary
}
