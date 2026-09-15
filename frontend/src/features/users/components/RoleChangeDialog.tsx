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

interface RoleChangeDialogProps {
  /** The account being changed, or null when the dialog is closed. */
  user: AdminUserSummary | null
  /**
   * The roles this reader may move, from `useUserAdminCapabilities` — in the order of #165.
   *
   * It arrives as a prop rather than being read here, so the dialog cannot disagree with the button
   * that opened it: the screen asks the capability question once and both sides of it use the answer.
   */
  grantableRoles: readonly PlatformRole[]
  open: boolean
  onOpenChange: (open: boolean) => void
  /**
   * The explanation of who may grant what, rendered under the role list.
   *
   * **It arrives as a node instead of the dialog raising it itself**, because `HelpLink` lives in
   * `features/help` and a feature never imports another one (arquitectura.md §3.1.5) - the one
   * component that does is `SearchQueryInput`, and it is in the transversal `components/` layer, not
   * in a feature. The screen owns the composition, so the link still sits where the question is
   * born rather than in an index somewhere.
   */
  help?: ReactNode
}

/**
 * Grants or takes away one platform role, with a reason (#169).
 *
 * **One role and one direction per press, not a checklist of four.** Grant and revoke are two
 * endpoints and each writes its own audit row with its own justification; a dialog that submitted a
 * diff would either send one reason for several changes or invent one per change without asking.
 * What direction a press means is not a second choice either — it is read off the account: a role
 * somebody has is taken away, one they do not have is given.
 *
 * **It only ever offers `grantableRoles`.** An admin finds no way at all to hand out `Admin` or
 * `Owner` here — not a disabled button, not one that fails when pressed: the rank is simply not on
 * the list (principio 2 de frontend-diseno.md §1). The server refuses it too, with
 * `ROLE_GRANT_FORBIDDEN`, which is what makes this a display decision rather than the security (#103).
 *
 * It owns the two mutations, as every `…Dialog` does (arquitectura.md §3.3, #110). The refusals it
 * can get are specific — the last owner, an owner unmaking themselves — so they are rendered inline,
 * above the button that was pressed, instead of as a toast.
 *
 * @param props.user           the account being changed
 * @param props.grantableRoles the roles this reader may move
 * @param props.open           whether the dialog is showing
 * @param props.onOpenChange   called when it is dismissed
 * @param props.help           the explanation of who may grant what, shown under the role list
 */
export function RoleChangeDialog({ user, grantableRoles, open, onOpenChange, help }: RoleChangeDialogProps) {
  const { t } = useTranslation('admin')
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

  function handleOpenChange(next: boolean) {
    if (!next) {
      form.reset({ justification: '' })
      grant.reset()
      revoke.reset()
    }
    onOpenChange(next)
  }

  function onSubmit(values: ChangeUserRoleForm) {
    if (!user) return
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
                    what lets somebody notice which ones this account already holds (#233's reasoning
                    for the cajones, and the same shape). */}
                <div className="flex flex-wrap gap-2">
                  {grantableRoles.map((role) => (
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
                    not the screen failing (#231 - the help is raised where it is asked for). */}
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
            <p role="alert" className="bg-state-canceled-bg text-state-canceled-fg rounded-md px-3 py-2 text-sm">
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
