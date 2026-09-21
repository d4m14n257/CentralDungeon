package com.centraldungeon.common.config;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where uploaded files are kept and what is allowed in, bound from {@code app.storage.*}.
 *
 * <p>All three values are configuration and not constants on purpose: the legacy backend had
 * <b>no limit at all</b> - {@code multer} was instantiated without {@code limits}, so there was no
 * cap on size, count or quota (M21.3) - and tightening any of these should never be a migration.
 *
 * <p><b>The per-file cap used to be here too, and F3.5 moved it</b> to
 * {@code files.max_file_size_mb} in {@code system_settings} (#141). The line between what stayed and
 * what left is who decides it: where the blobs live and which MIME types the parser accepts are
 * decisions of whoever deploys the application, while how big a file may be is a decision of
 * whoever runs the community. Keeping a copy of the cap here would have been a second source of
 * truth for one number, which is the only way the two can ever disagree.
 *
 * @param root             the directory the blobs live under. Never part of the repository: dev
 *                         points it at a gitignored folder and the test profile at a temporary one
 * @param allowedMimeTypes what may be uploaded, as MIME types. The starting point is the legacy's
 *                         own whitelist (M21.4) - the only part of its file handling worth keeping
 * @param retention        how long a file survives without being used before the purge marks it
 *                         gone (#75). Never a physical delete: that is the owner's, from the
 *                         administration menu (#66)
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String root, Set<String> allowedMimeTypes, Duration retention) {
}
