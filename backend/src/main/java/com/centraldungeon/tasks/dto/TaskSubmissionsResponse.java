package com.centraldungeon.tasks.dto;

import java.util.List;

/**
 * What a master sees when they open one task: what came in, and who has not answered.
 *
 * <p>The two halves travel together because they are read together - "seven of nine" is one thought,
 * and splitting it across two requests would let the screen show a roster that disagrees with the
 * answers above it.
 *
 * <p><b>Nothing here is an accusation.</b> {@code missing} is a list of people to talk to, not a list
 * of people to remove: not answering blocks nothing and evicts nobody (#70), and no endpoint in this
 * feature acts on this list.
 *
 * @param taskId      the task
 * @param submissions every answer handed in, oldest first. Answers accumulate and none replaces
 *                    another (#76), so one person can appear more than once
 * @param missing     the people who were asked and have not answered, derived from the audience at
 *                    read time and never stored
 * @param recipientCount how many people were asked in total - {@code missing.size()} plus the people
 *                    who did answer
 */
public record TaskSubmissionsResponse(
        String taskId,
        List<TaskSubmissionResponse> submissions,
        List<TaskRecipientResponse> missing,
        int recipientCount) {
}
