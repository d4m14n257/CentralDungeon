import { useTranslation } from 'react-i18next'

import { StatusBadge } from '@/components/StatusBadge'
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
 * **Two branches and no `Record`**, which is why this one keeps a shape of its own while the other
 * eight badges collapsed into `StatusBadge` (#261): what it shows is not a value of an enum but the
 * answer to a yes-or-no question, and the "yes" carries a second thing to read. The markup it used to
 * repeat is gone all the same - it is `StatusBadge` underneath, with the relative date as its trailing
 * child.
 *
 * @param props.item the row
 */
export function ClaimBadge({ item }: { item: AdminQueueItem }) {
  const { t, i18n } = useTranslation('admin')

  if (!isClaimedByReader(item)) {
    return <StatusBadge tone="draft" label={t('queue.unclaimed')} />
  }

  return (
    <StatusBadge tone="active" label={t('queue.claimedBy', { name: item.claimedByName })}>
      {item.claimedAt && <span className="opacity-80">{formatRelativeDate(item.claimedAt, i18n.language)}</span>}
    </StatusBadge>
  )
}
