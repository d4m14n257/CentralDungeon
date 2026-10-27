import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'

import { useGrantRole } from '../api/useGrantRole'
import { useRevokeRole } from '../api/useRevokeRole'
import { userAdminErrorKey } from '../adminErrors'
import { changeUserRoleSchema, type ChangeUserRoleForm } from '../schemas'
import type { AdminUserSummary, PlatformRole } from '../types'
import { useConfirm } from '@/hooks/useConfirm'

interface RoleChangeDialogProps {
  /** The account being changed, or null when the dialog is closed. */
  user: AdminUserSummary | null
  /**
   * The roles this reader may move, from `useUserAdminCapabilities` — in the order of ADR-07 §8.
   *
   * It arrives as a prop rather than being read here, so the dialog cannot disagree with the button
   * that opened it: the screen asks the capability question once and both sides of it use the answer.
   */
  grantableRoles: readonly PlatformRole[]
  /**
   * Whether the reader may take `Owner` away. `Owner` is never on `grantableRoles` — the application
   * does not grant it — so it is offered only to take away, only where the account holds it, and
   * never on the reader's own account (see `actorId`).
   */
  canRevokeOwner?: boolean
  /**
   * The reader's own id, to recognise their own account. On it an `Owner` is offered neither `Owner`
   * nor `Admin`: Admin and Owner are one rank, so taking one's own Owner away, directly or by giving
   * oneself Admin, is refused by the server as `CANNOT_REVOKE_OWN_OWNER`. Another owner has to do it.
   */
  actorId?: string | undefined
  open: boolean
  onOpenChange: (open: boolean) => void
  /**
   * The explanation of who may grant what, rendered under the role list.
   *
   * **It arrives as a node instead of the dialog raising it itself**, because `HelpLink` lives in
   * `features/help` and a feature never imports another one (arquitectura §3.1.5) - the one
   * component that does is `SearchQueryInput`, and it is in the transversal `components/` layer, not
   * in a feature. The screen owns the composition, so the link still sits where the question is
   * born rather than in an index somewhere.
   */
  help?: ReactNode
}

/**
 * Grants or takes away one platform role, with a reason (ADR-06 §1).
 *
 * **One role and one direction per press, not a checklist of four.** Grant and revoke are two
 * endpoints and each writes its own audit row with its own justification; a dialog that submitted a
 * diff would either send one reason for several changes or invent one per change without asking.
 * What direction a press means is not a second choice either — it is read off the account: a role
 * somebody has is taken away, one they do not have is given.
 *
 * **It only ever offers `grantableRoles`, plus `Owner` to take away.** An admin finds no way at all to
 * hand out `Admin`, and nobody finds a way to hand out `Owner` — not a disabled button, not one that
 * fails when pressed: the role is simply not on the list (principle 2 of diseno §1). `Owner` shows up
 * only for an owner, and only on another owner's account, because that is the one thing that can be
 * done with it; on their own account an owner sees neither `Owner` nor `Admin`. The server refuses the rest too, with `ROLE_GRANT_FORBIDDEN` and `OWNER_NOT_GRANTABLE`,
 * which is what makes this a display decision rather than the security (ADR-20 §1).
 *
 * It owns the two mutations, as every `…Dialog` does (arquitectura §3.3, ADR-19 §6). The refusals it
 * can get are specific — the last owner, an owner unmaking themselves — so they are rendered inline,
 * above the button that was pressed, instead of as a toast.
 *
 * @param props.user           the account being changed
 * @param props.grantableRoles the roles this reader may give and take away
 * @param props.canRevokeOwner whether the reader may take `Owner` away from an account that holds it
 * @param props.actorId        the reader's own id, to withhold `Owner` and `Admin` from an owner looking at their own account
 * @param props.open           whether the dialog is showing
 * @param props.onOpenChange   called when it is dismissed
 * @param props.help           the explanation of who may grant what, shown under the role list
 */
export function RoleChangeDialog({
  user,
  grantableRoles,
  canRevokeOwner = false,
  actorId,
  open,
  onOpenChange,
  help,
}: RoleChangeDialogProps) {
  const { t } = useTranslation('admin')
  const confirm = useConfirm()
  const grant = useGrantRole()
  const revoke = useRevokeRole()

  const form = useForm<ChangeUserRoleForm>({
    resolver: zodResolver(changeUserRoleSchema),
    defaultValues: { justification: '' },
  })

  const selectedRole = form.watch('role')
  const isPending = grant.isPending || revoke.isPending
  const errorKey = userAdminErrorKey(grant.error ?? revoke.error)

  /** Whether pressing on this role would take it away rather than hand it out. */
  function isHeld(role: PlatformRole): boolean {
    return user?.roles.includes(role) ?? false
  }

  const ownAccountAsOwner = user !== null && user.id === actorId && isHeld('Owner')
  const grantable = ownAccountAsOwner ? grantableRoles.filter((role) => role !== 'Admin') : grantableRoles
  const offeredRoles = canRevokeOwner && isHeld('Owner') && !ownAccountAsOwner ? [...grantable, 'Owner' as const] : grantable

  function handleOpenChange(next: boolean) {
    if (!next) {
      form.reset({ justification: '' })
      grant.reset()
      revoke.reset()
    }
    onOpenChange(next)
  }

  async function onSubmit(values: ChangeUserRoleForm) {
    if (!user) return
    // A review before anything is written (ADR-21 §9): what is about to change, and what follows.
    const confirmed = await confirm({
      title: t(isHeld(values.role) ? 'users.confirmRevokeTitle' : 'users.confirmGrantTitle', {
        role: t(`users.roles.${values.role}`),
        name: user.discordUsername,
      }),
      description: t('users.confirmRoleDescription'),
    })
    if (!confirmed) return
    const mutation = isHeld(values.role) ? revoke : grant
    const successKey = isHeld(values.role) ? 'users.revokeSuccess' : 'users.grantSuccess'
    mutation.mutate(
      { userId: user.id, input: values },
      {
        onSuccess: () => {
          toast.success(t(successKey, { role: t(`users.roles.${values.role}`), name: user.discordUsername }))
          handleOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={t('users.roleDialogTitle', { name: user?.discordUsername ?? '' })}
      description={t('users.roleDialogDescription')}
      isDirty={form.formState.isDirty}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          <FormField
            control={form.control}
            name="role"
            render={() => (
              <FormItem>
                <FormLabel>{t('users.roleFieldLabel')}</FormLabel>
                {/* Buttons and not a <Select>: there are at most four and seeing them all at once is
                    what lets somebody notice which ones this account already holds (ADR-14 §4's reasoning
                    for the cajones, and the same shape). */}
                <div className="flex flex-wrap gap-2">
                  {offeredRoles.map((role) => (
                    <button
                      key={role}
                      type="button"
                      aria-pressed={selectedRole === role}
                      onClick={() => form.setValue('role', role, { shouldDirty: true, shouldValidate: true })}
                      className={cn(
                        'rounded-full border px-3 py-1 text-xs font-medium transition-colors',
                        selectedRole === role ? 'border-brand-400 bg-raised text-fg' : 'border-border text-fg-muted hover:text-fg',
                      )}
                    >
                      {t(`users.roles.${role}`)}
                      <span className="text-fg-subtle ml-1.5">{isHeld(role) ? t('users.roleHeld') : t('users.roleNotHeld')}</span>
                    </button>
                  ))}
                </div>
                {/* Right under the list, because this is where the question is born: an admin sees
                    two of the four roles and needs to know that the absence is the rule working,
                    not the screen failing (ADR-20 §5 - the help is raised where it is asked for). */}
                {help}
                <FormMessage />
              </FormItem>
            )}
          />

          {/* What the press will do, said before it is pressed rather than after. */}
          {selectedRole && (
            <p className="text-fg-muted text-sm">
              {isHeld(selectedRole)
                ? t('users.roleWillRevoke', { role: t(`users.roles.${selectedRole}`), name: user?.discordUsername ?? '' })
                : t('users.roleWillGrant', { role: t(`users.roles.${selectedRole}`), name: user?.discordUsername ?? '' })}
            </p>
          )}

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
            <p role="alert" className="inline-error">
              {t(errorKey)}
            </p>
          )}

          <Button type="submit" disabled={isPending} className="w-full">
            {selectedRole && isHeld(selectedRole) ? t('users.revokeRole') : t('users.grantRole')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
