package com.centraldungeon.tables;

/** Not the platform role Owner (decisiones.md #67) - Primary is this table's Owner-equivalent. */
public enum MasterType {

    /** The table's owner. Exactly one live per table (#73). Shown as "master" (#166). */
    Primary,

    /** A co-master. Any number of them. Shown as "co-master" (#166). */
    Secondary
}
