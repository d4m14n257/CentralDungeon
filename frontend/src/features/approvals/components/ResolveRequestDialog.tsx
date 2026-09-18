import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'

import { useApproveRequest } from '../api/useApproveRequest'
import { useRejectRequest } from '../api/useRejectRequest'
import { approvalErrorKey } from '../approvalErrors'
import { resolveApprovalRequestSchema, type ResolveApprovalRequestForm } from '../schemas'
import type { ApprovalRequestSummary } from '../types'

/** Which of the two acts the dialog is performing. There is no third thing it could be asked to do. */
export type ResolveAction = 'approve' | 'reject'

interface ResolveRequestDialogProps {
  /** The request being resolved, or null when the dialog is closed. */
  request: ApprovalRequestSummary | null
  /** Approving or rejecting. The note is required either way (#42). */
  action: ResolveAction
  open: boolean
  onOpenChange: (open: boolean) => void
  /**
   * The explanation of what approving each kind does, shown above the note.
   *
   * A node rather than a `HelpLink` raised here: `features/help` is another feature and a feature
   * never imports one (arquitectura.md §3.1.5).
   */
  help?: ReactNode
}

/**
 * Resolving a request, in its two acts (#42).
 *
 * **The note is required on both of them.** The mechanism asks for a justification at each end, and
 * rejecting without one is the half that makes the other half worthless — whoever asked is told no
 * and has nothing to act on. Approving needs it just as much: it is the record of why the platform
 * granted something, and until F6 brings `audit_logs` it is the only one.
 *
 * **What was asked for is on screen while the note is being written.** An admin deciding on a
 * request needs the reason they were given in front of them, not one dialog back.
 *
 * **Approving is not the same act for every kind**, and the dialog says which one it is: a
 * `MasterGrant` hands out the role through the very same `UserRoleService` that `/admin/users` uses;
 * a `TableOpen` records that the request stands and creates nothing — the table is created
 * afterwards, by an admin, with a name and an agenda the request never carried (#72); a `General`
 * simply closes.
 *
 * @param props.request      the request being resolved
 * @param props.action       approving or rejecting
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called when it is dismissed
 * @param props.help         the explanation of what approving each kind does
 */
export function ResolveRequestDialog({ request, action, open, onOpenChange, help }: ResolveRequestDialogProps) {
  const { t } = useTranslation('admin')
  const approve = useApproveRequest()
  const reject = useRejectRequest()

  const isApproving = action === 'approve'
  const isPending = approve.isPending || reject.isPending
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
    const mutation = isApproving ? approve : reject
    mutation.mutate(
      { requestId: request.id, input: values },
      {
        onSuccess: () => {
          toast.success(t(isApproving ? 'requests.approveSuccess' : 'requests.rejectSuccess'))
          handleOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={t(isApproving ? 'requests.approveDialogTitle' : 'requests.rejectDialogTitle')}
      description={t(isApproving ? 'requests.approveDialogDescription' : 'requests.rejectDialogDescription')}
      isDirty={form.formState.isDirty}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          {/* What was asked, and by whom, while the answer is being written. */}
          {request && (
            <div className="border-border space-y-1 rounded-lg border border-dashed px-4 py-3 text-sm">
              <p className="text-fg-muted text-xs">
                {t('requests.askedBy', { name: request.requestedByName })} · {t(`requests.types.${request.type}`)}
              </p>
              <p className="whitespace-pre-wrap">{request.justification}</p>
            </div>
          )}

          {/* Only on the approval: what the platform is about to do differs by kind, and a rejection
              does the same thing for all three — it closes the row and says why. */}
          {isApproving && request && <p className="text-fg-muted text-sm">{t(`requests.effect.${request.type}`)}</p>}

          {help}

          <FormField
            control={form.control}
            name="resolutionNote"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('requests.resolutionNoteLabel')}</FormLabel>
                <FormControl>
                  <Textarea
                    rows={3}
                    placeholder={t(isApproving ? 'requests.approveNotePlaceholder' : 'requests.rejectNotePlaceholder')}
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {errorKey && (
            <p role="alert" className="bg-state-canceled-bg text-state-canceled-fg rounded-md px-3 py-2 text-sm">
              {t(errorKey)}
            </p>
          )}

          <Button type="submit" variant={isApproving ? 'default' : 'destructive'} disabled={isPending} className="w-full">
            {t(isApproving ? 'requests.approve' : 'requests.reject')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
