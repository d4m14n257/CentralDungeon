import { useTranslation } from 'react-i18next'

import { formatRelativeDate } from '@/lib/date'

import { isClaimedByReader, type AdminQueueItem } from '../types'

/**
 * Whether a tray row is reserved, by whom, and since when (#100).
 *
 * **The reservation has to be visible or nobody can use it.** Its whole value is telling colleagues
 * "I am on this one", and a mark nobody can see tells nobody anything. It is also what makes the one
 * refusal legible in advance: an admin who sees a row go from free to taken understands why the next
 * press answered that somebody else got there first.
 *
 * **"Hace cuánto" and not the exact time**, which is `formatRelativeDate`'s own reason to exist
 * (#75): the question a person asks of `claimedAt` is "is this about to be taken back from me", and a
 * formatted timestamp makes them do the subtraction themselves. The release job takes a stale
 * reservation back after `CLAIM_TIMEOUT_MINUTES` (`config/adminQueue.ts`), so the distance *is* the
 * meaning.
 *
 * **A name in `claimedByName` is always the reader's own** — the listing returns what is free or what
 * is theirs and nothing else (#100) — so the chip says "lo tenés vos" and names them for the row's
 * sake rather than asserting something about a third party the tray cannot see.
 *
 * Colour is never the only carrier (frontend-diseno.md §3): a dot plus a label, always. The class
 * names are complete literals because Tailwind 4 scans the source and cannot see a template string.
 *
 * @param props.item the row
 */
export function ClaimBadge({ item }: { item: AdminQueueItem }) {
  const { t, i18n } = useTranslation('admin')

  if (!isClaimedByReader(item)) {
    return (
      <span className="bg-state-draft-bg text-state-draft-fg inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium">
        <span className="bg-state-draft-dot size-1.5 rounded-full" />
        {t('queue.unclaimed')}
      </span>
    )
  }

  return (
    <span className="bg-state-active-bg text-state-active-fg inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium">
      <span className="bg-state-active-dot size-1.5 rounded-full" />
      {t('queue.claimedBy', { name: item.claimedByName })}
      {item.claimedAt && <span className="opacity-80">{formatRelativeDate(item.claimedAt, i18n.language)}</span>}
    </span>
  )
}
