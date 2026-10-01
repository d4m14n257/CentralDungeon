package com.centraldungeon.files.dto;

import com.centraldungeon.files.FileCategory;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * The metadata half of an admin's upload into the platform's library (#278, #282). The bytes and the
 * filename come from the multipart part itself.
 *
 * <p><b>What the file is and whether it goes out now are two separate answers.</b> The cajón is
 * always said at upload, so nothing sits in the library without saying which flow it is for; being
 * offered to masters is a choice, because a published file is attached by tables straight away and
 * hiding it later does not take it off them (#79).
 *
 * <p>The list cannot be empty - M24.1's fix, carried across from the audience it replaced: a file that
 * does not say which flow it is for turns up in front of the wrong people. The two player-side cajones
 * are refused by the service: they hold what individual people answered with, and a blank offered to
 * everybody is not an answer (#233).
 *
 * @param categories the cajones the file is offered in. At least one, all of them publishable
 * @param publish    true to publish it on upload; false to leave it in the library unpublished, for
 *                   an admin to publish when it is ready
 */
public record LibraryUploadRequest(@NotEmpty List<FileCategory> categories, boolean publish) {
}
