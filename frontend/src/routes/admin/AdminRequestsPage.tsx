import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'

import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import {
  PENDING_REQUESTS_QUERY,
  RequestDetailPanel,
  RequestStatusBadge,
  RequestTypeBadge,
  ResolveRequestDialog,
  approvalRequestSearchFields,
  useAdminRequests,
  type ApprovalRequestSummary,
  type ResolveAction,
} from '@/features/approvals'
import { HelpLink } from '@/features/help'
import { useDisclosure } from '@/hooks/useDisclosure'
import { useSearchQuery } from '@/hooks/useSearchQuery'
import { browserTimeZone, formatDate } from '@/lib/date'
import { ApiError } from '@/types/api'

/**
 * `/admin/requests` — every request somebody made of an admin, and the two things an admin can do
 * with one: approve it or turn it down, each with a reason (#42, F3.2).
 *
 * **It opens filtered by what is waiting.** A tray that opens showing what is already resolved is
 * not a tray anybody can work from — it is a log. The filter is a `?q=` like any other, put there by
 * this screen and not by the endpoint (`GET /admin/requests` with no `q` answers with everything,
 * like every other listing), so the `/status` chip is visible, editable and removable: seeing what
 * was decided is one click away rather than a different screen.
 *
 * **The empty tray is good news, not a broken screen** (#136, the same reading as `/master`):
 * nothing is waiting for an action. That is a different sentence from "your search found nothing",
 * and telling somebody who just filtered by `Rejected` that the platform is all caught up would
 * answer a question they did not ask.
 *
 * **Approving is not one act.** A `MasterGrant` hands the role out through the very same
 * `UserRoleService` that `/admin/users` uses — never a second road to the same grant
 * (fase-3-admin-owner.md:128) — while a `TableOpen` records that the request stands and creates
 * nothing: the request carries no name, no system, no seats and no agenda, so the table is created
 * afterwards from `/admin/tables` (#72). The dialog says which one is about to happen.
 *
 * **What was searched and which page are in the URL** (#185), like the other four admin screens: a
 * row is something one admin sends to another, and state that only lives in `useState` cannot be
 * linked to.
 *
 * One of the wide tables of frontend-diseno.md §5.b: below `md` it stops being a table and each row
 * becomes a card, built from the same column definitions — never horizontal scroll.
 *
 * **No role guard in front of it** (#103): somebody who forces the route without the role gets a
 * `403` from the backend and lands on `ForbiddenState`, which is an explanation rather than a blank
 * page.
 */
export function AdminRequestsPage() {
  const { t, i18n } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()
  const timeZone = browserTimeZone()

  const page = Number(searchParams.get('page') ?? '0')

  // The box holds a structured value; what travels - to the URL and to the API - is the raw string
  // of #164. With no `?q=` to restore, the tray starts on what is waiting: the URL says nothing and
  // the box shows the chip, which is the one state this screen is allowed to open in.
  const fields = useMemo(() => approvalRequestSearchFields(t), [t])
  const search = useSearchQuery({
    fields,
    initialQuery: searchParams.get('q') ?? PENDING_REQUESTS_QUERY,
    onQueryChange: (query) => updateParams({ q: query }),
  })

  const { data, isPending, isLoadingError, error, refetch } = useAdminRequests(search.debouncedQuery, page)

  const resolveDialog = useDisclosure<{ request: ApprovalRequestSummary; action: ResolveAction }>()
  const detailDialog = useDisclosure<ApprovalRequestSummary>()

  /** Writes the screen's state into the URL, resetting the page whenever the search changes. */
  function updateParams(changes: Record<string, string>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(changes)) {
      if (value === '') next.delete(key)
      else next.set(key, value)
    }
    if (!('page' in changes)) next.delete('page')
    setSearchParams(next, { replace: true })
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  // Whether the reader is looking at the tray as it opens - what is waiting - or at something they
  // narrowed themselves. The two have different empty states because they are different facts.
  const showsWhatIsWaiting = search.debouncedQuery.trim() === PENDING_REQUESTS_QUERY

  const columns: DataTableColumn<ApprovalRequestSummary>[] = [
    { id: 'requestedBy', header: t('requests.columns.requestedBy'), role: 'title', cell: (request) => request.requestedByName },
    {
      id: 'status',
      header: t('requests.columns.status'),
      role: 'badge',
      cell: (request) => <RequestStatusBadge status={request.status} />,
    },
    { id: 'type', header: t('requests.columns.type'), cell: (request) => <RequestTypeBadge type={request.type} /> },
    {
      id: 'justification',
      header: t('requests.columns.justification'),
      // The reason is the row: an admin decides on it and nothing else, so it is the one column
      // that must survive into the phone card.
      cell: (request) => <span className="line-clamp-2">{request.justification}</span>,
    },
    {
      id: 'createdAt',
      header: t('requests.columns.createdAt'),
      cell: (request) => formatDate(request.createdAt, i18n.language, timeZone),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('requests.title')}</h1>
        <HelpLink section="admins.requests" className="text-sm">
          {t('requests.helpLink')}
        </HelpLink>
      </div>
      <p className="text-fg-muted text-sm">{t('requests.description')}</p>

      <SearchQueryInput
        fields={search.fields}
        value={search.value}
        onChange={search.onChange}
        placeholder={t('requests.searchPlaceholder')}
        label={t('requests.searchLabel')}
      />

      {isPending && <Skeleton className="h-64 w-full" />}
      {/* isLoadingError, not isError: a failed background refetch must not blank a table that is
          already showing rows (#150). */}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState
          title={showsWhatIsWaiting ? t('requests.emptyPendingTitle') : t('requests.noResultsTitle')}
          description={showsWhatIsWaiting ? t('requests.emptyPendingDescription') : t('requests.noResultsDescription')}
        />
      )}
      {data && data.content.length > 0 && (
        <>
          <DataTable
            label={t('requests.title')}
            columns={columns}
            rows={data.content}
            getRowId={(request) => request.id}
            renderActions={(request) => (
              <div className="flex flex-wrap justify-end gap-2">
                {/* Absent on anything already resolved, never greyed out: a resolution is not
                    re-resolved, and a button that can only answer `REQUEST_ALREADY_RESOLVED` is a
                    button that should not be there (principio 2). */}
                {request.status === 'Pending' && (
                  <>
                    <Button size="sm" onClick={() => resolveDialog.open({ request, action: 'approve' })}>
                      {t('requests.approve')}
                    </Button>
                    <Button size="sm" variant="destructive" onClick={() => resolveDialog.open({ request, action: 'reject' })}>
                      {t('requests.reject')}
                    </Button>
                  </>
                )}
                {/* Reading the record is not an action on the request, so it is offered on every row
                    - and on a resolved one it is the only place the reason was written. */}
                <Button size="sm" variant="ghost" onClick={() => detailDialog.open(request)}>
                  {t('requests.detail')}
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

      {/* The dialog gets its help as a node rather than raising it itself: `HelpLink` lives in
          `features/help`, and a feature never imports another one (§3.1.5). The screen is what may
          compose the two, so the explanation still sits inside the dialog that prompts it (#231). */}
      <ResolveRequestDialog
        request={resolveDialog.item?.request ?? null}
        action={resolveDialog.item?.action ?? 'approve'}
        open={resolveDialog.isOpen}
        onOpenChange={(open) => !open && resolveDialog.close()}
        help={
          <p className="text-fg-subtle text-xs">
            {t('requests.resolveHelpHint')} <HelpLink section="admins.requests">{t('requests.resolveHelpLink')}</HelpLink>
          </p>
        }
      />

      <Dialog open={detailDialog.isOpen} onOpenChange={(open) => !open && detailDialog.close()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('requests.detailDialogTitle', { name: detailDialog.item?.requestedByName ?? '' })}</DialogTitle>
            <DialogDescription>{t('requests.detailDialogDescription')}</DialogDescription>
          </DialogHeader>
          {/* An id and not the row (§3.1.5): the panel asks for its own data, so what it shows is the
              request as it is now - which matters here, since another admin's resolution is exactly
              what is most likely to have landed in between. */}
          {detailDialog.item && <RequestDetailPanel requestId={detailDialog.item.id} />}
        </DialogContent>
      </Dialog>
    </div>
  )
}

export { AdminRequestsPage as Component }
