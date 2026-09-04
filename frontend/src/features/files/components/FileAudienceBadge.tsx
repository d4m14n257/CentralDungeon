import { useTranslation } from 'react-i18next'

import type { PublicAudience } from '../types'

/**
 * Who a published file is meant for (#64).
 *
 * Quieter than {@link FileTypeBadge} on purpose: it only ever appears next to one, and two loud
 * badges side by side would compete instead of reading as "published, for players".
 *
 * The audience decides where a file is *listed*, not who may open it — see the backend's
 * `FileService.listPublic`. So this label is a hint about intent, and the screen must not present it
 * as a lock.
 *
 * @param props.audience who the file is for
 */
export function FileAudienceBadge({ audience }: { audience: PublicAudience }) {
  const { t } = useTranslation('files')

  return (
    <span className="border-border text-fg-muted inline-flex items-center rounded-md border px-2 py-1 text-xs">
      {t(`audience.${audience}`)}
    </span>
  )
}
