import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useDebounce } from '@/hooks/useDebounce'

import { useMyFiles } from '../api/useMyFiles'
import { usePublicFiles } from '../api/usePublicFiles'
import { useUploadFile } from '../api/useUploadFile'
import { formatFileSize } from '../format'
import type { PublicAudience, StoredFile } from '../types'

interface FilePickerProps {
  /**
   * Called with the id of the file that was chosen — whether it was just uploaded or picked out of
   * the history. **The two paths end in the same callback on purpose**: to whoever is attaching, they
   * are the same decision, and the difference is only where the bytes came from (#65).
   */
  onPick: (fileId: string) => void
  /** True while the caller is doing something with the pick, to keep the buttons from firing twice. */
  isBusy?: boolean
  /**
   * Whether to offer what the platform published (#64, #79). False hides the tab entirely.
   */
  offerPublished?: boolean
  /**
   * Narrows the published tab to one audience, or undefined to offer everything published.
   *
   * **Undefined is the right answer for a master attaching to their table**, and getting this wrong
   * is what broke #79's own example: the community's default character sheet is published *for
   * players*, but the person who attaches it is the master. The audience says who ends up reading
   * the file, not who does the attaching, so narrowing by the picker's own audience would hide
   * exactly the file the feature exists to share.
   */
  publishedAudience?: PublicAudience
}

/**
 * Choosing a file: upload a new one, or reuse one that already exists (#65).
 *
 * **This is the cost lever of the whole fase** (#75, which repealed the per-user quota of #61 in
 * favour of attacking volume). Reuse only reduces anything if it is as easy as uploading, so the two
 * are peers here — two tabs of the same control, not an upload box with a link tucked underneath.
 * The same character sheet on a second table has to cost nothing.
 *
 * Three sources, and the third is what makes #79 real: a master attaching the community's default
 * sheet picks it from **Published** and links it, rather than downloading it and uploading a copy of
 * their own. That tab shows everything published unless a caller narrows it — see
 * {@link FilePickerProps.publishedAudience} for why narrowing it by the reader's own role is wrong.
 *
 * The per-file cap is stated up front rather than after a failed upload, because a limit somebody
 * only meets by breaking it is a limit that reads as a bug (principio 2 de frontend-diseno.md §1).
 *
 * @param props.onPick            called with the id of the chosen file
 * @param props.isBusy            true while the caller is acting on a pick
 * @param props.offerPublished    whether to offer what the platform published
 * @param props.publishedAudience narrows that tab to one audience, or undefined for all of it
 */
export function FilePicker({ onPick, isBusy = false, offerPublished = false, publishedAudience }: FilePickerProps) {
  const { t, i18n } = useTranslation('files')
  const [tab, setTab] = useState('upload')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search, 300)
  const fileInput = useRef<HTMLInputElement>(null)

  const upload = useUploadFile()
  const history = useMyFiles(debouncedSearch || undefined, 0, tab === 'reuse')
  const published = usePublicFiles(publishedAudience, tab === 'published' && offerPublished)

  function handleUpload(file: File) {
    // `Private` and not `SingleUse`: somebody who took the trouble to upload a character sheet will
    // want it again on the next table, and #65 only works if the history has anything in it. Letting
    // go of it is one click away in /admin/files or in the owner's own list; getting it back after a
    // purge is not (#68, #75).
    upload.mutate(
      { file, input: { fileType: 'Private' } },
      {
        onSuccess: (uploaded: StoredFile) => {
          onPick(uploaded.id)
          if (fileInput.current) {
            fileInput.current.value = ''
          }
        },
      },
    )
  }

  return (
    <Tabs value={tab} onValueChange={setTab} className="space-y-4">
      <TabsList>
        <TabsTrigger value="upload">{t('picker.upload')}</TabsTrigger>
        <TabsTrigger value="reuse">{t('picker.reuse')}</TabsTrigger>
        {offerPublished && <TabsTrigger value="published">{t('picker.published')}</TabsTrigger>}
      </TabsList>

      <TabsContent value="upload" className="space-y-2">
        <Input
          ref={fileInput}
          type="file"
          aria-label={t('picker.uploadLabel')}
          disabled={upload.isPending || isBusy}
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) {
              handleUpload(file)
            }
          }}
        />
        <p className="text-fg-muted text-xs">{t('picker.limits')}</p>
      </TabsContent>

      <TabsContent value="reuse" className="space-y-3">
        <Input
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('picker.searchPlaceholder')}
          aria-label={t('picker.searchLabel')}
        />
        {history.isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : history.data && history.data.content.length > 0 ? (
          <ul className="divide-border divide-y">
            {history.data.content.map((file) => {
              const size = formatFileSize(file.sizeBytes, i18n.language)
              return (
                <li key={file.id} className="flex items-center justify-between gap-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm">{file.name}</p>
                    <p className="text-fg-muted text-xs">{t(`size.${size.unit}`, { value: size.value })}</p>
                  </div>
                  <Button type="button" size="sm" variant="secondary" disabled={isBusy} onClick={() => onPick(file.id)}>
                    {t('picker.use')}
                  </Button>
                </li>
              )
            })}
          </ul>
        ) : (
          <EmptyState title={t('picker.historyEmptyTitle')} description={t('picker.historyEmptyDescription')} />
        )}
      </TabsContent>

      {offerPublished && (
        <TabsContent value="published" className="space-y-3">
          {published.isPending ? (
            <Skeleton className="h-24 w-full" />
          ) : published.data && published.data.content.length > 0 ? (
            <ul className="divide-border divide-y">
              {published.data.content.map((file) => {
                const size = formatFileSize(file.sizeBytes, i18n.language)
                return (
                  <li key={file.id} className="flex items-center justify-between gap-3 py-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm">{file.name}</p>
                      <p className="text-fg-muted text-xs">{t(`size.${size.unit}`, { value: size.value })}</p>
                    </div>
                    <Button type="button" size="sm" variant="secondary" disabled={isBusy} onClick={() => onPick(file.id)}>
                      {t('picker.use')}
                    </Button>
                  </li>
                )
              })}
            </ul>
          ) : (
            <EmptyState title={t('picker.publishedEmptyTitle')} description={t('picker.publishedEmptyDescription')} />
          )}
        </TabsContent>
      )}
    </Tabs>
  )
}
