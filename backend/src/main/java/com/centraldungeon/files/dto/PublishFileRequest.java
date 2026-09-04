package com.centraldungeon.files.dto;

import com.centraldungeon.files.PublicAudience;
import jakarta.validation.constraints.NotNull;

/**
 * Publishing a file for the whole platform, with the audience #64 requires.
 *
 * <p>Only an admin sends this, and the audience is not optional. A public file with no audience is
 * precisely the state M24.1 describes as broken: the legacy returned every public file everywhere,
 * so a document written for masters turned up in front of a player.
 *
 * @param publicAudience who the file is meant for: the people who run tables, the people who play, or
 *                       everybody
 */
public record PublishFileRequest(@NotNull PublicAudience publicAudience) {
}
