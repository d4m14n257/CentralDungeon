import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router'

import { RichTextView } from '@/components/RichTextView'
import { CatalogChip } from '@/features/catalogs'
import { tableTypeLabel, type GameTableDetail } from '@/features/tables'
import { formatPlainDate } from '@/lib/date'
import type { CatalogValue } from '@/types/catalog'

interface OutletContext {
  table: GameTableDetail
}

/**
 * The first tab of `/admin/tables/:id` (#284): the table as its master wrote it - what is played, how
 * and where, the text, the rules, the capacity.
 *
 * **The admin's tab and not the master's**: the master wrote all of this and reads it back on the edit
 * page, so their screen opens on the candidates. An admin arriving at a table they have never seen
 * needs to know what it is before anything else, which is why this goes first here.
 *
 * It reads the detail the screen already loaded and makes no request of its own. The agenda has its
 * own tab, with the zone it is shown in; repeating it here would be two places to keep in step.
 */
export function AdminTableDetailsTab() {
  const { t, i18n } = useTranslation('admin')
  const { t: tTables } = useTranslation('tables')
  const { t: tMaster } = useTranslation('master')
  const { table } = useOutletContext<OutletContext>()
  const catalogs: CatalogValue[] = [...table.systems, ...table.tags, ...table.platforms]
  const notSet = t('tables.detail.notSet')

  // The facts a moderator looks for first, each with its label and «Sin definir» rather than a blank:
  // a missing value is information too.
  const facts = [
    { label: tMaster('create.tableTypeLabel'), value: tableTypeLabel(tTables, table.tableTypeCode, table.tableTypeName) ?? notSet },
    { label: tMaster('create.startDateLabel'), value: table.startDate ? formatPlainDate(table.startDate, i18n.language) : notSet },
    {
      label: tTables('detail.capacity'),
      value:
        table.maxPlayers === null
          ? t('tables.detail.playersNoCap', { count: table.playerCount })
          : t('tables.detail.playersOfCap', { count: table.playerCount, max: table.maxPlayers }),
    },
    { label: tMaster('create.totalSessionsLabel'), value: table.totalSessions === null ? notSet : String(table.totalSessions) },
  ]

  return (
    <div className="space-y-6">
      <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {facts.map((fact) => (
          <div key={fact.label}>
            <dt className="section-label">{fact.label}</dt>
            <dd className="mt-1 text-sm">{fact.value}</dd>
          </div>
        ))}
      </dl>

      {catalogs.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {catalogs.map((value) => (
            <CatalogChip key={value.id} value={value} />
          ))}
        </div>
      )}

      <section>
        <h2 className="section-label">{tTables('detail.description')}</h2>
        {table.description ? (
          <RichTextView html={table.description} className="mt-1.5 max-w-prose" />
        ) : (
          <p className="text-fg-muted mt-1.5 text-sm">{notSet}</p>
        )}
      </section>

      {table.permitted && (
        <section>
          <h2 className="section-label">{tTables('detail.permitted')}</h2>
          <RichTextView html={table.permitted} className="mt-1.5 max-w-prose" />
        </section>
      )}

      {table.requirements && (
        <section>
          <h2 className="section-label">{tTables('detail.requirements')}</h2>
          <RichTextView html={table.requirements} className="mt-1.5 max-w-prose" />
        </section>
      )}
    </div>
  )
}

export { AdminTableDetailsTab as Component }
