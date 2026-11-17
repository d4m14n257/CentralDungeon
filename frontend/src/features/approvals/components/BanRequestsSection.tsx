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

import { useApproveRequest } from '../api/useApproveRequest'
import { useRejectRequest } from '../api/useRejectRequest'
import { useTableBanRequests } from '../api/useTableBanRequests'
import { approvalErrorKey } from '../approvalErrors'
import { resolveApprovalRequestSchema, type ResolveApprovalRequestForm } from '../schemas'
import type { BanRequest } from '../types'
import { useConfirm } from '@/hooks/useConfirm'

/** Granting the veto or refusing it. There is no third answer. */
type BanResolution = 'approve' | 'reject'

/**
 * An admin's answer to one veto request, written with the note both acts require.
 *
 * **Its own dialog rather than `ResolveRequestDialog`**, which is the same shape one folder over but
 * is built around a row of the admin tray: this one is opened from a table's players tab and names
 * the person the veto is about, because deciding about two open requests on the same table by the
 * wording of their reasons alone is deciding half-blind. Both go through the same generic approval
 * mutations.
 */
function ResolveBanRequestDialog({
  request,
  resolution,
  open,
  onOpenChange,
}: {
  request: BanRequest | null
  resolution: BanResolution
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation('master')
  const confirm = useConfirm()
  const approve = useApproveRequest()
  const reject = useRejectRequest()

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

  async function onSubmit(values: ResolveApprovalRequestForm) {
    if (!request) return
    // A review before anything is written (ADR-21 §9): what is about to change, and what follows.
    const confirmed = await confirm({
      title: t(isApproving ? 'banRequests.confirmApproveTitle' : 'banRequests.confirmRejectTitle', {
        name: request.targetUserName,
      }),
      description: t(isApproving ? 'banRequests.confirmApproveDescription' : 'banRequests.confirmRejectDescription'),
    })
    if (!confirmed) return
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
            <p role="alert" className="inline-error">
              {/* The sentences live in the `admin` namespace, where the mechanism's vocabulary
                  already is: one place where a refusal of a request has a wording (ADR-16 §7). */}
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
  /**
   * Whether the reader is an admin, who decides these. A master only reads the list: whoever asked
   * has to see the request is still waiting, or they will ask twice.
   */
  canResolve: boolean
  /** The help link the screen hands down — a feature never imports `features/help` (§3.1.5). */
  help?: ReactNode
}

/**
 * The veto requests waiting on one table. Masters read them; an admin answers them.
 *
 * **A section of this feature and not of the screen, receiving an id and doing its own query**
 * (§3.1.5): the players tab already composes three domains, and a block that fetched through props
 * would make the tab the owner of a query about a mechanism it knows nothing about.
 *
 * **Everyone who may open the list sees it; only an admin sees the buttons.** A master who asked
 * needs to see that the answer has not come, and the note above the list says who answers.
 * What is hidden from a master is the button they could not use, not the information.
 *
 * **It renders nothing at all when there is nothing waiting.** An empty block on a tab that is
 * mostly about something else is noise; the roster below is where the everyday work is.
 *
 * @param props.tableId    the table
 * @param props.canResolve whether the reader is an admin and may answer these
 * @param props.help       the explanation of what a veto is, raised by the screen
 */
export function BanRequestsSection({ tableId, canResolve, help }: BanRequestsSectionProps) {
  const { t, i18n } = useTranslation('master')
  // isLoadingError, not isError: a failed background refetch must not blank a list that loaded (ADR-19 §4).
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
        <p className="text-fg-muted text-xs">{t(canResolve ? 'banRequests.descriptionAdmin' : 'banRequests.descriptionMaster')}</p>
      </div>

      <ul className="list-divided-bare">
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
              {canResolve && (
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
        request={dialog.item?.request ?? null}
        resolution={dialog.item?.resolution ?? 'approve'}
        open={dialog.isOpen}
        onOpenChange={(open) => !open && dialog.close()}
      />
    </section>
  )
}
