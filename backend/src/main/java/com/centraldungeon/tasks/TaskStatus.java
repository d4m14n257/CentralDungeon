package com.centraldungeon.tasks;

/**
 * Where a task is in its short life: taking answers, done taking them, or gone.
 *
 * <p>There is no {@code Draft}. <b>Creating a task is publishing it</b>, because #77 makes
 * publication the moment the recipients are told, and a draft nobody was told about is a row that
 * does nothing. The screen's "publish" button and the {@code POST} are the same act.
 */
public enum TaskStatus {

    /** Taking answers. The only status in which a submission is accepted. */
    Open,

    /**
     * The master is no longer taking answers.
     *
     * <p>What was already submitted stays readable - closing ends the intake, it does not erase the
     * history (#76).
     */
    Closed,

    /** Soft delete (#25). The row survives; every read filters it out. */
    Deleted
}
