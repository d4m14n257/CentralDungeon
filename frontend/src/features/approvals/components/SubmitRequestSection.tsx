import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { useDisclosure } from '@/hooks/useDisclosure'
import { browserTimeZone, formatDate } from '@/lib/date'

import { useMyRequests } from '../api/useMyRequests'
import { PENDING_REQUESTS_QUERY } from '../requestTypes'
import { SubmitRequestDialog } from './SubmitRequestDialog'
import type { ApprovalRequestType } from '../types'

interface SubmitRequestSectionProps {
  /** What this particular screen lets somebody ask for. */
  type: ApprovalRequestType
  /**
   * The explanation of how a request works, handed to the dialog.
   *
   * A node and not a `HelpLink` raised here: a feature never imports another one (§3.1.5). The
   * screen composes the two, exactly as F3.1 did with the role and block dialogs.
   */
  help?: ReactNode
}

/**
 * The way a screen offers a request: one button, or the news that one is already waiting.
 *
 * **This is where principio 2 of `frontend-diseno.md` §1 is paid.** A second request of the same
 * kind is refused with `REQUEST_ALREADY_PENDING`, so a button offered while one is pending is a
 * button whose only possible outcome is a `409`. `GET /requests/mine?q=/status Pending` is what lets
 * the screen know the difference, and what is shown instead is the fact and its date — an answer,
 * rather than an absence somebody has to interpret.
 *
 * **Asking the server for the pending ones is what makes that answer trustworthy**, and not a saving.
 * The unfiltered listing carries resolved requests too, so a pending one can be buried under twenty
 * answered ones: reading a page of it would conclude there was none and offer the button straight
 * into the refusal. The filter is what turns "nothing on page one" into "nothing".
 *
 * It is written once and used from the three screens that prompt a request (fase-3-admin-owner.md:126)
 * rather than three times, because the rule — ask, or see that you already asked — is the same in all
 * three and three copies of it are how they start to disagree.
 *
 * **Nothing is drawn while the answer is in flight.** Painting the button first and taking it away a
 * moment later is worse than a beat of silence: whoever was reaching for it has already reached.
 *
 * @param props.type what this screen lets somebody ask for
 * @param props.help the explanation of how a request works, handed to the dialog
 */
export function SubmitRequestSection({ type, help }: SubmitRequestSectionProps) {
  const { t, i18n } = useTranslation('admin')
  const dialog = useDisclosure()
  // Only what is still waiting, asked of the server rather than filtered out of a page here: the
  // listing carries resolved requests too, so reading page one of it would miss an old pending
  // request buried under twenty answered ones and offer the button back. Filtered, the whole answer
  // is at most three rows - one per kind - so one page is provably all of it.
  const { data, isPending } = useMyRequests(PENDING_REQUESTS_QUERY)

  if (isPending) return null

  // A failed read leaves the button on offer rather than hiding it: not knowing whether somebody
  // already asked is not a reason to take away the only way they have to ask. The refusal, if it
  // comes, is rendered inside the dialog.
  const waiting = data?.content.find((request) => request.type === type && request.status === 'Pending')

  if (waiting) {
    return (
      <p className="text-fg-muted text-sm">
        {t(`requests.pending.${type}`)}{' '}
        <span className="text-fg-subtle">
          {t('requests.pendingSince', { date: formatDate(waiting.createdAt, i18n.language, browserTimeZone()) })}
        </span>
      </p>
    )
  }

  return (
    <>
      <Button size="sm" variant="outline" onClick={() => dialog.open()}>
        {t(`requests.ask.${type}`)}
      </Button>
      <SubmitRequestDialog type={type} open={dialog.isOpen} onOpenChange={(open) => !open && dialog.close()} help={help} />
    </>
  )
}
