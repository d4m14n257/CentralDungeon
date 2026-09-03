package com.centraldungeon.notifications;

/** E1 minimal set - more types arrive with the features that emit them. */
public enum NotificationType {

    /** To the applicant: a master accepted them into a table. */
    RegistrationAccepted,

    /** To the applicant: their application was turned down, with the master's reason. */
    RegistrationRejected,

    /** To every master of the table, Primary and Secondary alike: someone applied. */
    NewCandidate
}
