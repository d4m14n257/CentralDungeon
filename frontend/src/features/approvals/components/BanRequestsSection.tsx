import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { ErrorState } from '@/components/ErrorState'
import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { useDisclosure } from '@/hooks/useDisclosure'
import { formatRelativeDate } from '@/lib/date'

import { useApproveBanRequest, useRejectBanRequest } from '../api/useResolveBanRequest'
import { useTableBanRequests } from '../api/useTableBanRequests'
import { approvalErrorKey } from '../approvalErrors'
import { resolveApprovalRequestSchema, type ResolveApprovalRequestForm } from '../schemas'
import type { BanRequest } from '../types'

/** Granting the veto or refusing it. There is no third answer. */
type BanResolution = 'approve' | 'reject'

/**
 * The answer to one veto request, written with the note both acts require (#42).
 *
 * **Its own dialog rather than `ResolveRequestDialog`**, which is the same shape one folder over.
 * That one is welded to `useApproveRequest`/`useRejectRequest` — the admin tray's mutations, on
 * `/admin/requests/{id}` — and a veto request is resolved by the table's `Primary` on the table's
 * own route (#39). Threading a pair of mutations through as props would turn a component that reads
 * plainly today into one whose behaviour is decided by its caller, for two uses.
 */
function ResolveBanRequestDialog({
  tableId,
  request,
  resolution,
  open,
  onOpenChange,
}: {
  tableId: string
  request: BanRequest | null
  resolution: BanResolution
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation('master')
  const approve = useApproveBanRequest(tableId)
  const reject = useRejectBanRequest(tableId)

  const isApproving = resolution === 'approve'
  const mutation = isApproving ? approve : reject
  const errorKey = approvalErrorKey(approve.error ?? reject.error)

  const form = useForm<ResolveApprovalRequestForm>({
    resolver: zodResolver(resolveApprovalRequestSchema),
    defaultValues: { resolutionNote: '' },
  })

  function handleOpenChange(next: boolean) {
    if (!next) {
      form.reset({ resolutionNote: '' })
      approve.reset()
      reject.reset()
    }
    onOpenChange(next)
  }

  function onSubmit(values: ResolveApprovalRequestForm) {
    if (!request) return
    mutation.mutate(
      { requestId: request.requestId, input: values },
      {
        onSuccess: () => {
          toast.success(t(isApproving ? 'banRequests.approveSuccess' : 'banRequests.rejectSuccess'))
          handleOpenChange(false)
        },
      },
    )
  }

  // The title names the person, because that is what is being decided: a dialog headed "Aceptar el
  // pedido de veto" over two open requests is a decision taken half-blind.
  const title = t(isApproving ? 'banRequests.approveDialogTitle' : 'banRequests.rejectDialogTitle', {
    name: request?.targetUserName ?? '',
  })

  return (
    <FormDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={title}
      description={t(isApproving ? 'banRequests.approveDialogDescription' : 'banRequests.rejectDialogDescription')}
      isDirty={form.formState.isDirty}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          {/* Who, by whom, and why — all while the answer is being written, not one dialog back. */}
          {request && (
            <div className="border-border space-y-1 rounded-lg border border-dashed px-4 py-3 text-sm">
              <p className="text-fg-muted text-xs">
                {t('banRequests.about', { name: request.targetUserName })} · {t('banRequests.askedBy', { name: request.requestedByName })}
              </p>
              <p className="whitespace-pre-wrap">{request.justification}</p>
            </div>
          )}
          {/* Approving is not "marcar como visto": it applies the veto then and there. */}
          {isApproving && (
            <p className="text-fg-muted text-sm">{t('banRequests.approveEffect', { name: request?.targetUserName ?? '' })}</p>
          )}
          <FormField
            control={form.control}
            name="resolutionNote"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('banRequests.resolutionNoteLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={3} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          {errorKey !== null && (
            <p role="alert" className="bg-state-canceled-bg text-state-canceled-fg rounded-md px-3 py-2 text-sm">
              {/* The sentences live in the `admin` namespace, where the mechanism's vocabulary
                  already is: one place where a refusal of a request has a wording (#176). */}
              {t(errorKey, { ns: 'admin' })}
            </p>
          )}
          <Button type="submit" variant={isApproving ? 'destructive' : 'default'} disabled={mutation.isPending} className="w-full">
            {t(isApproving ? 'banRequests.approve' : 'banRequests.reject')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}

interface BanRequestsSectionProps {
  tableId: string
  /** Whether the reader runs the table. Only the `Primary` answers these (#39). */
  isPrimary: boolean
  /** The help link the screen hands down — a feature never imports `features/help` (§3.1.5). */
  help?: ReactNode
}

/**
 * The veto requests waiting on one table, and the `Primary`'s answer to them (#39, F3.4).
 *
 * **A section of this feature and not of the screen, receiving an id and doing its own query**
 * (§3.1.5): the players tab already composes three domains, and a block that fetched through props
 * would make the tab the owner of a query about a mechanism it knows nothing about.
 *
 * **Both masters see the list; only the `Primary` sees the buttons.** A co-master who asked needs to
 * see that the answer has not come — otherwise they ask again, and #39's whole point is that the
 * decision belongs to one person. Principio 2 is satisfied rather than violated by that: what is
 * hidden from the co-master is the button they could not use, not the information.
 *
 * **It renders nothing at all when there is nothing waiting.** An empty block on a tab that is
 * mostly about something else is noise; the roster below is where the everyday work is.
 *
 * @param props.tableId   the table
 * @param props.isPrimary whether the reader may answer these
 * @param props.help      the explanation of what a veto is, raised by the screen
 */
export function BanRequestsSection({ tableId, isPrimary, help }: BanRequestsSectionProps) {
  const { t, i18n } = useTranslation('master')
  // isLoadingError, not isError: a failed background refetch must not blank a list that loaded (#150).
  const { data, isPending, isLoadingError, refetch } = useTableBanRequests(tableId)
  const dialog = useDisclosure<{ request: BanRequest; resolution: BanResolution }>()

  if (isPending) {
    return <Skeleton className="h-16 w-full" />
  }
  if (isLoadingError) {
    return <ErrorState onRetry={() => void refetch()} />
  }
  if (data.length === 0) {
    return null
  }

  return (
    <section className="border-border space-y-3 rounded-lg border p-4">
      <div className="space-y-1">
        <h3 className="text-sm font-medium">{t('banRequests.title')}</h3>
        <p className="text-fg-muted text-xs">{t(isPrimary ? 'banRequests.descriptionPrimary' : 'banRequests.descriptionSecondary')}</p>
      </div>

      <ul className="divide-border divide-y">
        {data.map((request) => (
          <li key={request.requestId} className="space-y-2 py-3 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="min-w-0 space-y-0.5">
                {/* **The person, first and in full weight.** This is the line the shape was changed
                    for: two open requests on the same table used to be distinguishable only by the
                    wording of their reasons, which is no way to decide about somebody. */}
                <p className="font-medium">{t('banRequests.about', { name: request.targetUserName })}</p>
                <p className="text-fg-muted text-xs">
                  {t('banRequests.askedBy', { name: request.requestedByName })} · {formatRelativeDate(request.createdAt, i18n.language)}
                </p>
              </div>
              {isPrimary && (
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="destructive" onClick={() => dialog.open({ request, resolution: 'approve' })}>
                    {t('banRequests.approve')}
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => dialog.open({ request, resolution: 'reject' })}>
                    {t('banRequests.reject')}
                  </Button>
                </div>
              )}
            </div>
            {/* The reason is what the decision is made on, so it is on the row and not behind a click. */}
            <p className="whitespace-pre-wrap">{request.justification}</p>
          </li>
        ))}
      </ul>

      {help}

      <ResolveBanRequestDialog
        tableId={tableId}
        request={dialog.item?.request ?? null}
        resolution={dialog.item?.resolution ?? 'approve'}
        open={dialog.isOpen}
        onOpenChange={(open) => !open && dialog.close()}
      />
    </section>
  )
}
