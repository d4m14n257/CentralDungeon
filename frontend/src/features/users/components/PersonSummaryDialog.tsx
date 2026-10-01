import { useTranslation } from 'react-i18next'

import { ErrorState } from '@/components/ErrorState'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'

import { useUserProfile } from '../api/useUserProfile'

/** What {@link PersonSummaryDialog} takes. */
export interface PersonSummaryDialogProps {
  /** The person to show, or null while the dialog is closed. */
  userId: string | null
  /** Whether the dialog is open. */
  open: boolean
  /** Called when it opens or closes. */
  onOpenChange: (open: boolean) => void
}

/**
 * A person's card as a master reads it from their table (#284): who they are on the server and how
 * many tables they saw through - the general picture, and nothing an admin would act on.
 *
 * **Deliberately short, and the line between the two roles** (#284): a master deciding on a candidate
 * or looking at a player needs to know who that is, not their account's standing; the full record -
 * status, roles, every table, what admins did to it - is the admin's, on /admin/users/:id.
 *
 * It reads the same profile the visibility rules already gate (#41, #47), so the master only ever
 * opens it for somebody linked to their table. **No karma and no comments yet** (#248): both are F5,
 * and the card says nothing about them rather than drawing an empty section.
 *
 * @param props.userId       the person
 * @param props.open         whether it is open
 * @param props.onOpenChange called when it opens or closes
 */
export function PersonSummaryDialog({ userId, open, onOpenChange }: PersonSummaryDialogProps) {
  const { t } = useTranslation('users')
  const { data: profile, isPending, isLoadingError, refetch } = useUserProfile(open && userId ? userId : '')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{profile ? (profile.name ?? profile.discordUsername) : t('summary.title')}</DialogTitle>
          <DialogDescription>{t('summary.description')}</DialogDescription>
        </DialogHeader>
        {isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : isLoadingError || !profile ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : (
          <dl className="grid grid-cols-2 gap-4">
            <div>
              <dt className="section-label">{t('summary.discord')}</dt>
              <dd className="mt-1 text-sm">{profile.discordUsername}</dd>
            </div>
            <div>
              <dt className="section-label">{t('summary.name')}</dt>
              <dd className="mt-1 text-sm">{profile.name ?? t('profile.noName')}</dd>
            </div>
            <div>
              <dt className="section-label">{t('summary.finishedTables')}</dt>
              <dd className="mt-1 text-sm">{t('summary.finishedTablesValue', { count: profile.finishedTables })}</dd>
            </div>
          </dl>
        )}
      </DialogContent>
    </Dialog>
  )
}
