package com.centraldungeon.features.files.dto;

import com.centraldungeon.features.files.FileCategory;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * The metadata half of an admin's upload into the platform's library. The bytes and the
 * filename come from the multipart part itself.
 *
 * <p><b>What the file is and whether it goes out now are two separate answers.</b> The category is
 * always said at upload, so nothing sits in the library without saying which flow it is for; being
 * offered to masters is a choice, because a published file is attached by tables straight away and
 * hiding it later does not take it off them.
 *
 * <p>The list cannot be empty, as the audience it replaced already required: a file that
 * does not say which flow it is for turns up in front of the wrong people. The two player-side categories
 * are refused by the service: they hold what individual people answered with, and a blank offered to
 * everybody is not an answer.
 *
 * @param categories the categories the file is offered in. At least one, all of them publishable
 * @param publish    true to publish it on upload; false to leave it in the library unpublished, for
 *                   an admin to publish when it is ready
 */
public record LibraryUploadRequest(@NotEmpty List<FileCategory> categories, boolean publish) {
}
