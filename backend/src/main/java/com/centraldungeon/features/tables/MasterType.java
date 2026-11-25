package com.centraldungeon.features.tables;

/** Not the platform role Owner - Primary is this table's Owner-equivalent. */
public enum MasterType {

    /** The table's owner. Exactly one live per table. Shown as "master". */
    Primary,

    /** A co-master. Any number of them. Shown as "co-master". */
    Secondary
}
