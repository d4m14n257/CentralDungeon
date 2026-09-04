package com.centraldungeon.common.config;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Where uploaded files are kept and what is allowed in, bound from {@code app.storage.*}.
 *
 * <p>All four values are configuration and not constants on purpose: the legacy backend had
 * <b>no limit at all</b> - {@code multer} was instantiated without {@code limits}, so there was no
 * cap on size, count or quota (M21.3) - and tightening any of these should never be a migration.
 *
 * @param root             the directory the blobs live under. Never part of the repository: dev
 *                         points it at a gitignored folder and the test profile at a temporary one
 * @param maxFileSize      the cap per file. The one piece of #61 that #75 kept when it repealed the
 *                         per-user quota; the levers that replaced it are reuse, compression and
 *                         the unused-file purge
 * @param allowedMimeTypes what may be uploaded, as MIME types. The starting point is the legacy's
 *                         own whitelist (M21.4) - the only part of its file handling worth keeping
 * @param retention        how long a file survives without being used before the purge marks it
 *                         gone (#75). Never a physical delete: that is the owner's, from the
 *                         administration menu (#66)
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String root, DataSize maxFileSize, Set<String> allowedMimeTypes, Duration retention) {
}
