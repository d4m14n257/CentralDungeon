import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'

import { useBlockUser } from '../api/useBlockUser'
import { useUnblockUser } from '../api/useUnblockUser'
import { userAdminErrorKey } from '../adminErrors'
import { changeUserStatusSchema, type ChangeUserStatusForm } from '../schemas'
import type { AdminUserSummary } from '../types'

interface BlockUserDialogProps {
  /** The account whose status is being changed, or null when the dialog is closed. */
  user: AdminUserSummary | null
  open: boolean
  onOpenChange: (open: boolean) => void
  /**
   * The explanation of what a block does and who is out of reach of it, shown next to the two
   * consequences.
   *
   * A node rather than a `HelpLink` raised here, for the same reason as {@link RoleChangeDialog}:
   * `features/help` is another feature and a feature never imports one (arquitectura.md §3.1.5).
   */
  help?: ReactNode
}

/**
 * Closes an account, or reopens one (#84). Which of the two it is comes from the account's own
 * status — there is no third thing this dialog could be asked to do.
 *
 * **It says what a block means before the button is pressed**, which is the whole reason it is a
 * dialog and not a confirm: "block" is a word people read as "delete", and the two consequences that
 * matter pull in opposite directions. The person stops being able to sign in — that is the point —
 * **and nothing of theirs is removed**: their tables, their applications, their sessions and their
 * history all stay. Learning the second half afterwards is learning it too late.
 *
 * **A justification is required**, not offered. Until F6 brings `audit_logs`, the row this writes is
 * the only record of why an account was closed, and a row with an empty reason is a row nobody can
 * review (#84). The server enforces it too, with `@NotBlank`.
 *
 * The dialog is never reached for an account holding `Admin` or `Owner`: the screen does not offer
 * the button, and the server refuses it with `CANNOT_BLOCK_PRIVILEGED` to anybody arriving another
 * way. Between peers there is no authority, and a block cannot be appealed from the inside.
 *
 * @param props.user         the account whose status is being changed
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called when it is dismissed
 * @param props.help         the explanation of what a block does, shown with the consequences
 */
export function BlockUserDialog({ user, open, onOpenChange, help }: BlockUserDialogProps) {
  const { t } = useTranslation('admin')
  const block = useBlockUser()
  const unblock = useUnblockUser()

  const isUnblocking = user?.status === 'Blocked'
  const isPending = block.isPending || unblock.isPending
  const errorKey = userAdminErrorKey(block.error ?? unblock.error)

  const form = useForm<ChangeUserStatusForm>({
    resolver: zodResolver(changeUserStatusSchema),
    defaultValues: { justification: '' },
  })

  function handleOpenChange(next: boolean) {
    if (!next) {
      form.reset({ justification: '' })
      block.reset()
      unblock.reset()
    }
    onOpenChange(next)
  }

  function onSubmit(values: ChangeUserStatusForm) {
    if (!user) return
    const mutation = isUnblocking ? unblock : block
    mutation.mutate(
      { userId: user.id, input: values },
      {
        onSuccess: () => {
          toast.success(t(isUnblocking ? 'users.unblockSuccess' : 'users.blockSuccess', { name: user.discordUsername }))
          handleOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={t(isUnblocking ? 'users.unblockDialogTitle' : 'users.blockDialogTitle', { name: user?.discordUsername ?? '' })}
      description={t(isUnblocking ? 'users.unblockDialogDescription' : 'users.blockDialogDescription')}
      isDirty={form.formState.isDirty}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          {/* The two consequences, spelled out and not implied (#84). Only on the block: reopening an
              account has one consequence and everybody already knows what it is. */}
          {!isUnblocking && (
            <ul className="border-border text-fg-muted space-y-1 rounded-lg border border-dashed px-4 py-3 text-sm">
              <li>{t('users.blockConsequenceAccess')}</li>
              <li>{t('users.blockConsequenceData')}</li>
            </ul>
          )}

          {/* Offered on both acts, not only the block: "why can I not block this person" and "what
              does unblocking restore" are asked from the same place (#231). */}
          {help}

          <FormField
            control={form.control}
            name="justification"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('users.justificationLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={3} placeholder={t('users.justificationPlaceholder')} {...field} />
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

          <Button type="submit" variant={isUnblocking ? 'default' : 'destructive'} disabled={isPending} className="w-full">
            {t(isUnblocking ? 'users.unblock' : 'users.block')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
