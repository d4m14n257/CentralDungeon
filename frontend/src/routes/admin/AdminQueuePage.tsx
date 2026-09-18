import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'

import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { CLAIM_TIMEOUT_MINUTES } from '@/config/adminQueue'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  ADMIN_QUEUE_ERROR_CODES,
  ClaimBadge,
  QueueItemKindBadge,
  adminQueueErrorKey,
  isClaimedByReader,
  useAdminQueue,
  useClaimItem,
  useReleaseItem,
  type AdminQueueItem,
} from '@/features/adminQueue'
import { approvalErrorKey, useApproveRequest, useRejectRequest } from '@/features/approvals'
import { HelpLink } from '@/features/help'
import { JustifiedTableActionDialog, useApproveTable, useRequestChanges } from '@/features/tables'
import { useConfirm } from '@/hooks/useConfirm'
import { useDisclosure } from '@/hooks/useDisclosure'
import { formatRelativeDate } from '@/lib/date'
import { ApiError } from '@/types/api'

/** The three acts that are written with a note. Approving a table is the fourth, and it is a confirm. */
type QueueAction = 'requestChanges' | 'approveRequest' | 'rejectRequest'

/**
 * What the row says it is, in the reader's language.
 *
 * **A table's `title` is its name and a request's is the wire name of its kind** — `MasterGrant`, not
 * "Rol de master". That is the price of normalizing four sources into one shape: the tray answers the
 * same five questions about every one of them and cannot translate for a vocabulary it does not own,
 * so the value travels canonical (#253) and is read here.
 *
 * The labels are the ones `/admin/requests` already shows, **reused and not copied** (#176): one
 * place where a kind of request has a name. `defaultValue` is the fallback, so a kind F5 adds before
 * its translation lands reads as its own name rather than as a bare key.
 *
 * @param item the row
 * @param t    the translator of the `admin` namespace
 * @returns the title to render
 */
function itemTitle(item: AdminQueueItem, t: TFunction): string {
  if (item.kind !== 'ApprovalRequest') return item.title
  return t(`requests.types.${item.title}`, { defaultValue: item.title })
}

/**
 * The sentence to show over a refused resolution.
 *
 * Two vocabularies meet on this screen and neither owns the other: the reservation's code is the
 * tray's (`features/adminQueue`), and everything a request can be refused with is the approvals
 * feature's. A screen that composes two features has to compose their refusals too — mapping them all
 * through one of the two would answer `ITEM_ALREADY_CLAIMED` with "no pudimos completar la acción",
 * and that is the one refusal here whose sentence actually tells the reader what happened: a
 * colleague got to it inside the fifteen seconds between two polls.
 *
 * @param error what the mutation rejected with
 * @returns the key to render, or null when there is nothing to say
 */
function resolutionErrorKey(error: unknown): string | null {
  if (error instanceof ApiError && (ADMIN_QUEUE_ERROR_CODES as readonly string[]).includes(error.problem.errorCode)) {
    return adminQueueErrorKey(error)
  }
  return approvalErrorKey(error)
}

/**
 * `/admin/queue` — the shared admin tray (#100, F3.3): everything waiting on an admin, whichever
 * table the work lives in, with the thing that has been waiting longest first.
 *
 * **The home of the admin context**, like `/master` is the master's (#136, #220): the screen a
 * context opens on is the one that says what to do next, not one of its listings. `/admin/tables`
 * answers "which tables exist", which is a question somebody asks on purpose.
 *
 * **Reserving is not permission, it is courtesy with teeth.** Taking an item drops it out of every
 * other admin's tray, so two people cannot spend the evening on the same request without knowing —
 * which is the whole of what #100 buys. It is **not** a precondition for resolving: every row this
 * screen can show is either free or already the reader's, and resolving a free one takes it
 * implicitly. So the resolution buttons are offered on **every** row, and principio 2 is satisfied by
 * that rather than violated: there is no row here whose resolution could only answer a refusal. The
 * rule the reservation does enforce — you cannot resolve what a colleague holds — is invisible on
 * this screen precisely because such a row is filtered out of it, and shows up on `/admin/requests`,
 * which lists every request there is.
 *
 * **The reservation is visible, and so is its age.** A claim is a promise with a deadline — the
 * backend hands a stale one back after fifteen minutes — so the row says who took it and how long
 * ago rather than making anybody subtract two timestamps.
 *
 * **It refreshes itself** (`live.adminQueue` in `config/query.ts`). The acceptance of this slice is
 * that an item a colleague reserves disappears *without a manual reload*, and invalidation after a
 * mutation cannot see what somebody else did. The WebSocket of F6 replaces the polling (#101).
 *
 * **The empty tray is good news** (#136, the same reading as `/master` and `/admin/requests`):
 * nothing is waiting for an action. The tray has no search box, deliberately — a work list sorts
 * itself by age and empties, so there is only ever one empty state to tell, and no "your search found
 * nothing" to distinguish it from.
 *
 * **Which page is in the URL** (#185), like every other admin screen: a tray is something one admin
 * points another at.
 *
 * One of the wide tables of frontend-diseno.md §5.b: below `md` it stops being a table and each row
 * becomes a card, built from the same column definitions — never horizontal scroll.
 *
 * **No role guard in front of it** (#103): somebody who forces the route without the role gets a
 * `403` from the backend and lands on `ForbiddenState`, which is an explanation rather than a blank
 * page.
 */
export function AdminQueuePage() {
  const { t, i18n } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()
  const confirm = useConfirm()

  const page = Number(searchParams.get('page') ?? '0')

  const { data, isPending, isLoadingError, error, refetch } = useAdminQueue(page)

  const claimItem = useClaimItem()
  const releaseItem = useReleaseItem()
  const approveTable = useApproveTable()
  const requestChanges = useRequestChanges()
  const approveRequest = useApproveRequest()
  const rejectRequest = useRejectRequest()

  const actionDialog = useDisclosure<{ item: AdminQueueItem; action: QueueAction }>()

  /** Writes the screen's state into the URL. The tray has one piece of it: which page. */
  function updateParams(changes: Record<string, string>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(changes)) {
      if (value === '') next.delete(key)
      else next.set(key, value)
    }
    setSearchParams(next, { replace: true })
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  /**
   * Takes or hands back a row.
   *
   * **What taking it buys is that nobody else spends the evening on the same thing**: the row drops
   * out of every other admin's tray until it is released or the job takes it back. It is not what
   * permits the resolution — the buttons next to it work on a free row too — which is why this sits
   * at the quiet end of the row as a `ghost`: it is what you press *before* reading something long,
   * not something the platform makes you press.
   *
   * The refusal goes to a toast rather than inline because there is no form open: the press was on
   * the row itself, and a toast lands where the reader is already looking. The dialogs below do the
   * opposite, for the same reason in reverse.
   */
  function claimOrRelease(item: AdminQueueItem) {
    const mutation = isClaimedByReader(item) ? releaseItem : claimItem
    mutation.mutate(
      { type: item.type, id: item.id },
      {
        onSuccess: () => toast.success(t(isClaimedByReader(item) ? 'queue.releaseSuccess' : 'queue.claimSuccess')),
        onError: (failure) => toast.error(t(adminQueueErrorKey(failure) ?? 'queue.errors.generic')),
      },
    )
  }

  /**
   * Approving a table carries no note, so it is a confirmation and not a form (#175's shape).
   *
   * **And therefore the refusal goes to a toast**: there is no dialog left open to put it over, the
   * confirmation having closed on the press. It is written from the code like every other one (#197)
   * — the failure worth a sentence here is a colleague having taken the table inside the fifteen
   * seconds between two polls, and "no pudimos completar la acción" would leave the reader pressing
   * the button again on a row that is about to vanish from their tray.
   */
  async function approveTableRow(item: AdminQueueItem) {
    const confirmed = await confirm({ title: t('queue.approveConfirmTitle'), description: t('queue.approveConfirmDescription') })
    if (!confirmed) return
    approveTable.mutate(item.id, {
      onSuccess: () => toast.success(t('queue.approveSuccess')),
      onError: (failure) => toast.error(t(adminQueueErrorKey(failure) ?? 'queue.errors.generic')),
    })
  }

  /** The three acts that are written with a note, from the one dialog that asks for one. */
  function submitNote(justification: string) {
    const pending = actionDialog.item
    if (!pending) return
    const { item, action } = pending

    if (action === 'requestChanges') {
      requestChanges.mutate(
        { tableId: item.id, request: { justification } },
        {
          onSuccess: () => {
            toast.success(t('queue.requestChangesSuccess'))
            actionDialog.close()
          },
        },
      )
      return
    }

    const mutation = action === 'approveRequest' ? approveRequest : rejectRequest
    mutation.mutate(
      { requestId: item.id, input: { resolutionNote: justification } },
      {
        onSuccess: () => {
          toast.success(t(action === 'approveRequest' ? 'requests.approveSuccess' : 'requests.rejectSuccess'))
          actionDialog.close()
        },
      },
    )
  }

  const columns: DataTableColumn<AdminQueueItem>[] = [
    { id: 'item', header: t('queue.columns.item'), role: 'title', cell: (item) => itemTitle(item, t) },
    { id: 'kind', header: t('queue.columns.kind'), role: 'badge', cell: (item) => <QueueItemKindBadge kind={item.kind} /> },
    // The reservation is the one thing on this row that changes under the reader's feet, so it is
    // what gets the colour (frontend-diseno.md §3: a dot and a label, never colour alone).
    { id: 'claim', header: t('queue.columns.claim'), role: 'badge', cell: (item) => <ClaimBadge item={item} /> },
    { id: 'requestedBy', header: t('queue.columns.requestedBy'), cell: (item) => item.requestedByName },
    {
      id: 'detail',
      header: t('queue.columns.detail'),
      // The reason is what an admin decides on, so it survives into the phone card. A table carries
      // none: sending one in for review says nothing in words.
      cell: (item) => (item.detail === null ? null : <span className="line-clamp-2">{item.detail}</span>),
    },
    {
      id: 'waitingSince',
      header: t('queue.columns.waitingSince'),
      // "Hace tres días" and not a date: the tray's order *is* this number, and urgency here is time
      // (#136). A formatted timestamp would make the reader do the subtraction themselves.
      cell: (item) => formatRelativeDate(item.waitingSince, i18n.language),
    },
  ]

  const dialogAction = actionDialog.item?.action ?? 'requestChanges'
  const isRequestAction = dialogAction !== 'requestChanges'
  const dialogError = resolutionErrorKey(
    dialogAction === 'requestChanges'
      ? requestChanges.error
      : dialogAction === 'approveRequest'
        ? approveRequest.error
        : rejectRequest.error,
  )

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('queue.title')}</h1>
        <HelpLink section="admins.claiming" className="text-sm">
          {t('queue.helpLink')}
        </HelpLink>
      </div>
      <p className="text-fg-muted text-sm">{t('queue.description')}</p>

      {isPending && <Skeleton className="h-64 w-full" />}
      {/* isLoadingError, not isError: a failed background refetch must not blank a table that is
          already showing rows (#150) — and this table refetches every fifteen seconds. */}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && <EmptyState title={t('queue.emptyTitle')} description={t('queue.emptyDescription')} />}
      {data && data.content.length > 0 && (
        <>
          <DataTable
            label={t('queue.title')}
            columns={columns}
            rows={data.content}
            getRowId={(item) => `${item.type}:${item.id}`}
            renderActions={(item) => (
              <div className="flex flex-wrap items-center justify-end gap-2">
                {/* Offered on every row, free or already taken. Principio 2 is what *allows* this
                    rather than what forbade it: a free row can be resolved — doing so reserves it —
                    so hiding the button would hide an action that works. What the reader cannot
                    resolve is a colleague's row, and such a row is not in this listing at all. */}
                {item.kind === 'TableWaitingReview' && (
                  <>
                    <Button size="sm" onClick={() => void approveTableRow(item)} disabled={approveTable.isPending}>
                      {t('queue.approve')}
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => actionDialog.open({ item, action: 'requestChanges' })}>
                      {t('queue.requestChanges')}
                    </Button>
                  </>
                )}
                {item.kind === 'ApprovalRequest' && (
                  <>
                    <Button size="sm" onClick={() => actionDialog.open({ item, action: 'approveRequest' })}>
                      {t('requests.approve')}
                    </Button>
                    <Button size="sm" variant="destructive" onClick={() => actionDialog.open({ item, action: 'rejectRequest' })}>
                      {t('requests.reject')}
                    </Button>
                  </>
                )}
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => claimOrRelease(item)}
                  disabled={claimItem.isPending || releaseItem.isPending}
                >
                  {t(isClaimedByReader(item) ? 'queue.release' : 'queue.claim')}
                </Button>
              </div>
            )}
          />
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onPageChange={(next) => updateParams({ page: String(next) })}
          />
        </>
      )}

      {/* One dialog for the three acts that carry a note, because they are one shape: read what was
          asked, write why, send. The help arrives as a node rather than being raised inside the
          dialog: `features/help` is another feature and a feature never imports one (§3.1.5). */}
      <JustifiedTableActionDialog
        open={actionDialog.isOpen}
        onOpenChange={(open) => !open && actionDialog.close()}
        title={
          dialogAction === 'requestChanges'
            ? t('queue.requestChangesDialogTitle', { name: actionDialog.item?.item.title ?? '' })
            : t(dialogAction === 'approveRequest' ? 'requests.approveDialogTitle' : 'requests.rejectDialogTitle')
        }
        description={
          dialogAction === 'requestChanges'
            ? t('queue.requestChangesDialogDescription')
            : t(dialogAction === 'approveRequest' ? 'requests.approveDialogDescription' : 'requests.rejectDialogDescription')
        }
        submitLabel={
          dialogAction === 'requestChanges'
            ? t('queue.requestChanges')
            : t(dialogAction === 'approveRequest' ? 'requests.approve' : 'requests.reject')
        }
        justificationLabel={isRequestAction ? t('requests.resolutionNoteLabel') : undefined}
        destructive={dialogAction === 'rejectRequest'}
        isPending={requestChanges.isPending || approveRequest.isPending || rejectRequest.isPending}
        errorMessage={dialogError === null ? null : t(dialogError)}
        context={
          actionDialog.item && (
            <div className="border-border space-y-1 rounded-lg border border-dashed px-4 py-3 text-sm">
              <p className="text-fg-muted text-xs">
                {t('queue.askedBy', { name: actionDialog.item.item.requestedByName })} · {itemTitle(actionDialog.item.item, t)}
              </p>
              {actionDialog.item.item.detail !== null && <p className="whitespace-pre-wrap">{actionDialog.item.item.detail}</p>}
            </div>
          )
        }
        help={
          <p className="text-fg-subtle text-xs">
            {t('queue.claimHelpHint', { minutes: CLAIM_TIMEOUT_MINUTES })}{' '}
            <HelpLink section="admins.claiming">{t('queue.claimHelpLink')}</HelpLink>
          </p>
        }
        onConfirm={submitNote}
      />
    </div>
  )
}

export { AdminQueuePage as Component }
