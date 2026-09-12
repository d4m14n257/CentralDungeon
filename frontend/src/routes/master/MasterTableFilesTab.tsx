import { EyeIcon, EyeOffIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { FormDialog } from '@/components/FormDialog'
import { IconAction } from '@/components/IconAction'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { HelpLink } from '@/features/help'
import {
  FileList,
  FilePicker,
  FileTypeBadge,
  useAttachTableFile,
  useCommitStagedFiles,
  useDetachTableFile,
  useTableFiles,
  useUpdateTableFile,
  type StagedFile,
  type TableFile,
  type TableFileType,
} from '@/features/files'

interface OutletContext {
  tableId: string
}

/**
 * The **Archivos** tab of /master/tables/:id — what the table has attached and who can see each thing.
 *
 * Two ideas the screen has to keep apart, because confusing them is the easiest mistake here (#79):
 *
 * - **Sharing is about this table.** The same map can be shared here and kept private on another
 *   table; the toggle acts on the attachment, never on the file.
 * - **Removing is about this table too.** Taking a file off does not delete it — it stays in the
 *   owner's own list and on every other table that has it. The confirmation says so, because
 *   "remove" reads like "delete" unless something says otherwise (principio 3 de frontend-diseno.md §1).
 *
 * A child route rather than local state (§3.1.6 regla 5), so the tab has its own URL, survives a
 * refresh and can be linked to.
 */
export function MasterTableFilesTab() {
  const { t } = useTranslation('files')
  const { tableId } = useOutletContext<OutletContext>()
  const confirm = useConfirm()

  const { data: files, isPending, isLoadingError, refetch } = useTableFiles(tableId)
  const attach = useAttachTableFile(tableId)
  const updateAttachment = useUpdateTableFile(tableId)
  const detach = useDetachTableFile(tableId)

  const [isAttaching, setIsAttaching] = useState(false)
  const [staged, setStaged] = useState<StagedFile[]>([])
  const commit = useCommitStagedFiles()
  const [kind, setKind] = useState<TableFileType>('Preparation')
  const [isPrivate, setIsPrivate] = useState(false)

  /**
   * Sends what was staged and attaches it (#238).
   *
   * Nothing uploaded when the file was picked, so this is both steps: upload, then link. A file that
   * fails leaves the others attached and stays listed to try again - the table is the master's and
   * editable, so there is nothing to roll back.
   */
  function attachStaged() {
    commit.mutate(
      { staged },
      {
        onSuccess: async ({ fileIds, failed }) => {
          for (const fileId of fileIds) {
            await attach.mutateAsync({ fileId, tableFileType: kind, isPrivate })
          }
          setStaged(staged.filter((entry) => entry.kind === 'new' && failed.includes(entry.name)))
          if (failed.length > 0) {
            toast.error(t('table.someFailed', { names: failed.join(', ') }))
            return
          }
          setIsAttaching(false)
          toast.success(t('table.attach'))
        },
      },
    )
  }

  async function handleDetach(file: TableFile) {
    const confirmed = await confirm({ title: t('table.detachTitle'), description: t('table.detachDescription') })
    if (!confirmed) return
    detach.mutate(file.fileId)
  }

  function handleToggleShared(file: TableFile) {
    updateAttachment.mutate({
      fileId: file.fileId,
      input: { tableFileType: file.tableFileType, isPrivate: !file.isPrivate },
    })
  }

  if (isPending) {
    return <Skeleton className="h-40 w-full" />
  }

  if (isLoadingError || !files) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('table.title')}</h2>
        <Button type="button" size="sm" onClick={() => setIsAttaching(true)}>
          {t('table.attach')}
        </Button>
      </div>

      {files.length === 0 ? (
        <EmptyState title={t('table.emptyTitle')} description={t('table.emptyDescription')} />
      ) : (
        <FileList
          files={files}
          renderMeta={(file) => (
            <div className="flex flex-wrap items-center gap-2">
              <FileTypeBadge fileType={file.fileType} />
              <span className="text-fg-muted text-xs">{t(`tableFileType.${file.tableFileType}`)}</span>
              <span className="text-fg-muted text-xs">{file.isPrivate ? t('table.privateBadge') : t('table.sharedBadge')}</span>
            </div>
          )}
          renderActions={(file) => (
            <>
              <IconAction
                label={file.isPrivate ? t('actions.share') : t('actions.unshare')}
                icon={file.isPrivate ? <EyeIcon className="size-4" /> : <EyeOffIcon className="size-4" />}
                disabled={updateAttachment.isPending}
                onClick={() => handleToggleShared(file)}
              />
              <IconAction
                label={t('actions.detach')}
                icon={<Trash2Icon className="size-4" />}
                disabled={detach.isPending}
                onClick={() => void handleDetach(file)}
              />
            </>
          )}
        />
      )}

      <HelpLink section="masters.files" className="inline-block text-xs">
        {t('table.helpLink')}
      </HelpLink>

      <FormDialog
        isDirty={kind !== 'Preparation' || isPrivate}
        open={isAttaching}
        onOpenChange={(open) => {
          setIsAttaching(open)
          if (!open) setStaged([])
        }}
        title={t('table.attachTitle')}
        description={t('table.attachDescription')}
      >
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="attach-kind">{t('table.kindLabel')}</Label>
            <Select value={kind} onValueChange={(value) => setKind(value as TableFileType)}>
              <SelectTrigger id="attach-kind">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="Preparation">{t('tableFileType.Preparation')}</SelectItem>
                <SelectItem value="Session">{t('tableFileType.Session')}</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="flex items-start gap-2">
            <Checkbox id="attach-private" checked={isPrivate} onCheckedChange={(checked) => setIsPrivate(checked === true)} />
            <div className="space-y-1">
              <Label htmlFor="attach-private">{t('table.privateLabel')}</Label>
              <p className="text-fg-muted text-xs">{t('table.privateHint')}</p>
            </div>
          </div>

          {/* Everything published is offered, not only the `Masters` audience: the community's
              default character sheet is published *for players* and the master is the one attaching
              it, which is #79's own example (#64). */}
          <FilePicker
            onPick={(picked) => setStaged((current) => [...current, picked])}
            isBusy={commit.isPending || attach.isPending}
            offerPublished
            cajon="TableMaterial"
            staged={staged}
            onRemove={(key) =>
              setStaged((current) => current.filter((entry) => (entry.kind === 'new' ? entry.localId : entry.fileId) !== key))
            }
          />
          <div className="flex justify-end">
            <Button type="button" disabled={staged.length === 0 || commit.isPending} onClick={attachStaged}>
              {t('table.attachConfirm', { count: staged.length })}
            </Button>
          </div>
        </div>
      </FormDialog>
    </div>
  )
}

export { MasterTableFilesTab as Component }
