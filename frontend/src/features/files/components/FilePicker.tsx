import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useDebounce } from '@/hooks/useDebounce'
import { formatRelativeDate } from '@/lib/date'

import { useMyFiles } from '../api/useMyFiles'
import { usePublicFiles } from '../api/usePublicFiles'
import { FileCard } from './FileCard'
import { FileCategoryBadge } from './FileCategoryBadge'
import { FileCategoryFilter } from './FileCategoryFilter'
import { FileDropzone } from './FileDropzone'
import type { FileCategory } from '../types'

interface FilePickerProps {
  /**
   * Called with the file that was chosen — whether it was just uploaded or picked out of the
   * history. **The two paths end in the same callback on purpose**: to whoever is attaching, they
   * are the same decision, and the difference is only where the bytes came from (#65).
   *
   * The name travels alongside the id because some callers show the pick back before doing anything
   * with it — answering a request lets several files be gathered and then sent (#76) — and looking a
   * name up again for something the picker just had is a round trip for nothing.
   */
  onPick: (file: { fileId: string; name: string }) => void
  /** True while the caller is doing something with the pick, to keep the buttons from firing twice. */
  isBusy?: boolean
  /**
   * Whether to offer what the platform published (#64, #79). False hides the tab entirely.
   */
  offerPublished?: boolean
  /**
   * The cajón this picker is standing in (#233) — the flow the file is being chosen for.
   *
   * **It is what the published tab asks for**, and it is how the community's blanks reach the right
   * moment: a master attaching to their table passes `TableMaterial` and gets the sheet published for
   * that; the same master writing a request passes `MasterRequest` and gets the forms published for
   * that. The old audience could not do this — it said who ends up reading the file, which is a
   * different question, and narrowing by it hid exactly the file #79 exists to share.
   */
  cajon?: FileCategory
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
 * **The published tab is narrowed to the cajón the picker stands in** (#233), which is what makes it
 * usable once the community has published more than a handful — and what puts the right blank in
 * front of the right moment without anybody filtering by hand.
 *
 * @param props.onPick         called with the chosen file's id and name
 * @param props.isBusy         true while the caller is acting on a pick
 * @param props.offerPublished whether to offer what the platform published
 * @param props.cajon          the flow the file is being chosen for; narrows the published tab
 */
export function FilePicker({ onPick, isBusy = false, offerPublished = false, cajon }: FilePickerProps) {
  const { t, i18n } = useTranslation('files')
  const [tab, setTab] = useState('upload')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search, 300)
  const [historyCategory, setHistoryCategory] = useState<FileCategory | null>(null)

  const history = useMyFiles(debouncedSearch || undefined, historyCategory ?? undefined, 0, tab === 'reuse')
  // Narrowed to the cajón this picker stands in: what the community published *for this moment*.
  const published = usePublicFiles(cajon, tab === 'published' && offerPublished)

  return (
    <Tabs value={tab} onValueChange={setTab} className="space-y-4">
      <TabsList>
        <TabsTrigger value="upload">{t('picker.upload')}</TabsTrigger>
        <TabsTrigger value="reuse">{t('picker.reuse')}</TabsTrigger>
        {offerPublished && <TabsTrigger value="published">{t('picker.published')}</TabsTrigger>}
      </TabsList>

      <TabsContent value="upload">
        <FileDropzone onUploaded={(uploaded) => onPick({ fileId: uploaded.id, name: uploaded.name })} isBusy={isBusy} />
      </TabsContent>

      <TabsContent value="reuse" className="space-y-3">
        <Input
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('picker.searchPlaceholder')}
          aria-label={t('picker.searchLabel')}
        />
        <FileCategoryFilter value={historyCategory} onChange={setHistoryCategory} />
        {history.isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : history.data && history.data.content.length > 0 ? (
          <ul className="divide-border divide-y">
            {history.data.content.map((file) => (
              <li key={file.id}>
                <FileCard
                  name={file.name}
                  mimeType={file.mimeType}
                  sizeBytes={file.sizeBytes}
                  meta={
                    <div className="flex flex-wrap items-center gap-2">
                      {file.categories.map((category) => (
                        <FileCategoryBadge key={category} category={category} />
                      ))}
                      {file.lastUsedAt && (
                        <span className="text-fg-muted text-xs">
                          {t('picker.lastUsed', { when: formatRelativeDate(file.lastUsedAt, i18n.language) })}
                        </span>
                      )}
                    </div>
                  }
                  actions={
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      disabled={isBusy}
                      onClick={() => onPick({ fileId: file.id, name: file.name })}
                    >
                      {t('picker.use')}
                    </Button>
                  }
                />
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState
            title={search || historyCategory ? t('picker.noResultsTitle') : t('picker.historyEmptyTitle')}
            description={search || historyCategory ? t('picker.noResultsDescription') : t('picker.historyEmptyDescription')}
          />
        )}
      </TabsContent>

      {offerPublished && (
        <TabsContent value="published" className="space-y-3">
          {published.isPending ? (
            <Skeleton className="h-24 w-full" />
          ) : published.data && published.data.content.length > 0 ? (
            <ul className="divide-border divide-y">
              {published.data.content.map((file) => (
                <li key={file.id}>
                  <FileCard
                    name={file.name}
                    mimeType={file.mimeType}
                    sizeBytes={file.sizeBytes}
                    meta={
                      <div className="flex flex-wrap items-center gap-2">
                        {file.categories.map((category) => (
                          <FileCategoryBadge key={category} category={category} />
                        ))}
                      </div>
                    }
                    actions={
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        disabled={isBusy}
                        onClick={() => onPick({ fileId: file.id, name: file.name })}
                      >
                        {t('picker.use')}
                      </Button>
                    }
                  />
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState title={t('picker.publishedEmptyTitle')} description={t('picker.publishedEmptyDescription')} />
          )}
        </TabsContent>
      )}
    </Tabs>
  )
}
