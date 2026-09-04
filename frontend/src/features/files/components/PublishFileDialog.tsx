import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import type { AdminFile, PublicAudience } from '../types'

/** The three audiences of #64, in the order they read: narrowest first, everybody last. */
const AUDIENCES: PublicAudience[] = ['Masters', 'Players', 'Announcements']

interface PublishFileDialogProps {
  /** The file being published, or null when the dialog is closed. */
  file: AdminFile | null
  onOpenChange: (open: boolean) => void
  /** Called with the audience the admin chose. */
  onConfirm: (audience: PublicAudience) => void
  isPending: boolean
}

/**
 * Publishing a file for the whole platform, with the audience #64 requires.
 *
 * **The audience is a required choice and there is no default**, which is the fix for M24.1: the
 * legacy returned every public file everywhere, so a document written for masters turned up in front
 * of a player. Making it a decision rather than a checkbox is what stops that from happening by
 * omission.
 *
 * The dialog says what publishing does, because it is the least reversible thing on the screen:
 * masters start attaching the file, and unpublishing it later does not take it off their tables (#79).
 *
 * @param props.file         the file being published, or null when closed
 * @param props.onOpenChange closes the dialog
 * @param props.onConfirm    called with the chosen audience
 * @param props.isPending    true while the request is in flight
 */
export function PublishFileDialog({ file, onOpenChange, onConfirm, isPending }: PublishFileDialogProps) {
  const { t } = useTranslation('files')
  const [audience, setAudience] = useState<PublicAudience>('Players')

  return (
    <FormDialog
      open={file !== null}
      onOpenChange={onOpenChange}
      title={t('publish.title')}
      description={t('publish.description', { name: file?.name ?? '' })}
    >
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="publish-audience">{t('publish.audienceLabel')}</Label>
          <Select value={audience} onValueChange={(value) => setAudience(value as PublicAudience)}>
            <SelectTrigger id="publish-audience">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {AUDIENCES.map((option) => (
                <SelectItem key={option} value={option}>
                  {t(`audience.${option}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-fg-muted text-xs">{t('publish.audienceHint')}</p>
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            {t('publish.cancel')}
          </Button>
          <Button type="button" disabled={isPending} onClick={() => onConfirm(audience)}>
            {t('publish.confirm')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
