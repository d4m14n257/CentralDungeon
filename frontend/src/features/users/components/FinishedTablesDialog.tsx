import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { DialogContent } from '@/components/DialogContent'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { PaginationControls } from '@/components/PaginationControls'
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { tableDetailPath } from '@/config/paths'
import { browserTimeZone, formatDate } from '@/lib/date'

import { useFinishedTables } from '../api/useFinishedTables'

/** What {@link FinishedTablesDialog} takes. */
export interface FinishedTablesDialogProps {
  /** Whose finished tables to list. */
  userId: string
  /** Whether the viewer may open a table's public detail: only an account with the Player role can. */
  canOpenTables: boolean
  /** Whether the dialog is open. */
  open: boolean
  /** Called when it opens or closes. */
  onOpenChange: (open: boolean) => void
}

/**
 * The tables somebody played to the end, as the finished-tables count on their profile opens them: name,
 * game systems, masters and closing date of each - what the table's public detail already says - one
 * page at a time.
 *
 * A name links to the table's public detail only when the viewer can open it (`canOpenTables`);
 * otherwise it is plain text, because that screen would send them back where they came from.
 *
 * @param props.userId        whose tables
 * @param props.canOpenTables whether names are links
 * @param props.open          whether it is open
 * @param props.onOpenChange  called when it opens or closes
 */
export function FinishedTablesDialog({ userId, canOpenTables, open, onOpenChange }: FinishedTablesDialogProps) {
  const { t, i18n } = useTranslation('users')
  const [page, setPage] = useState(0)
  const { data, isPending, isLoadingError, refetch } = useFinishedTables(userId, page, open)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="dialog-panel-scroll">
        <DialogHeader>
          <DialogTitle>{t('finishedTables.title')}</DialogTitle>
          <DialogDescription>{t('finishedTables.description')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-3 overflow-y-auto">
          {isPending ? (
            <Skeleton className="h-24 w-full" />
          ) : isLoadingError || !data ? (
            <ErrorState onRetry={() => void refetch()} />
          ) : data.content.length === 0 ? (
            <EmptyState title={t('finishedTables.emptyTitle')} description={t('finishedTables.emptyDescription')} />
          ) : (
            <>
              <ul className="list-divided-bare">
                {data.content.map((table) => (
                  <li key={table.id} className="space-y-1 py-3">
                    <p className="font-medium">
                      {canOpenTables ? (
                        <Link to={tableDetailPath(table.id)} className="hover:underline">
                          {table.name}
                        </Link>
                      ) : (
                        table.name
                      )}
                    </p>
                    <p className="text-fg-muted text-sm">
                      {[
                        table.systems.length > 0 && t('finishedTables.systems', { systems: table.systems.join(', ') }),
                        table.masters.length > 0 && t('finishedTables.masters', { masters: table.masters.join(', ') }),
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </p>
                    {table.closedAt && (
                      <p className="text-fg-subtle text-xs">
                        {t('finishedTables.closedOn', { date: formatDate(table.closedAt, i18n.language, browserTimeZone()) })}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
              <PaginationControls page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={setPage} />
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
