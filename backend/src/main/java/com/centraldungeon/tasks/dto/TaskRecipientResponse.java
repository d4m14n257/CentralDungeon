package com.centraldungeon.tasks.dto;

/**
 * One person a task was addressed to, for the master's roster of who has not answered.
 *
 * <p>Just a name and an id, on purpose. This list exists so a master can go and talk to somebody, and
 * anything more here would start to look like a file on them.
 *
 * @param userId   the person
 * @param userName how to name them on screen - their Discord username, which everybody has
 */
public record TaskRecipientResponse(String userId, String userName) {
}
