import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadMore } from '@/components/LoadMore'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Skeleton } from '@/components/ui/skeleton'
import { SubmitRequestSection } from '@/features/approvals'
import { HelpLink } from '@/features/help'
import { useMyApplications } from '@/features/registrations'
import { explorerSearchFields, GameTableCard, useGameTables } from '@/features/tables'
import { useSearchQuery } from '@/hooks/useSearchQuery'

/**
 * The explorer, `/player` - the open tables anyone can apply to.
 *
 * A master never finds their own table here: a table has one set of people who play at it and a
 * disjoint set who run it (#154), and the backend filters by the actor rather than the screen
 * hiding rows after the fact.
 *
 * **The search box is the only filter** (#242, and the same reading as `/my/files`). The row of
 * dropdowns the sitemap once described asked the same three questions the commands answer better —
 * `/table_system`, `/table_tag` and `/table_platform` narrow from the same line and combine with the
 * rest — and a term typed without a command searches the table's name.
 *
 * **Searching by one synonym finds the tables labelled with another** (#54, #56). The table keeps
 * the alias its master chose (#58) and the backend resolves the whole group, so nobody has to know
 * which of `D&D`, `DND` or `Dungeons & Dragons` the master happened to type.
 *
 * **What was searched lives in the URL** (#185): a filtered explorer can be linked to and survives a
 * refresh. The page number does not — this listing accumulates with "See more" (#173), so there is
 * no page number to keep.
 *
 * **Asking for a table to be opened happens from the empty explorer** and not from a screen for
 * making requests (fase-3-admin-owner.md:126): this is the moment somebody looked for a table and
 * found none, which is the whole of what a `TableOpen` request says. Approving it does not create
 * the table — the request carries no name, no system, no seats and no agenda — it records that the
 * request stands, and an admin opens one (#72).
 */
export function TableListPage() {
  const { t } = useTranslation('tables')
  // The request lives in the `admin` namespace with the rest of the mechanism (#42): one wording for
  // what a request is, read by whoever makes one and by whoever resolves it.
  const { t: tAdmin } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()

  // The box holds a structured value; what travels - to the URL and to the API - is the raw string
  // of #164. One list of commands, given to the box, used to read `?q=` and shown in the help (#240).
  const fields = useMemo(() => explorerSearchFields(t), [t])
  const search = useSearchQuery({
    fields,
    initialQuery: searchParams.get('q') ?? '',
    onQueryChange: (query) => {
      const next = new URLSearchParams(searchParams)
      if (query.trim()) next.set('q', query)
      else next.delete('q')
      setSearchParams(next, { replace: true })
    },
  })

  // isLoadingError, not isError: a background refetch that fails must not hide a list that already
  // loaded fine - TanStack Query sets isError to true anyway, without dropping the cached data
  // (docs/decisiones.md #150). The global connection indicator already says something is wrong.
  const { data, isPending, isLoadingError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useGameTables(
    search.debouncedQuery || undefined,
  )
  // A crossing of domains: the screen composes them, GameTableCard does not import from
  // features/registrations (regla dura 16). "Rejected" does not count as applied - applying again is
  // exactly what makes sense there.
  const { data: myApplications } = useMyApplications()
  const appliedTableIds = new Set(
    myApplications?.content.filter((registration) => registration.status !== 'Rejected').map((registration) => registration.gameTableId),
  )

  // The explorer paginates with "See more" (#173): the pages already fetched accumulate and show together.
  const tables = data?.pages.flatMap((page) => page.content) ?? []
  const total = data?.pages[0]?.totalElements ?? 0

  // Offered from both empty states and written once: "nothing is open yet" and "nothing matched what
  // you typed" are two different facts, but the thing somebody can do about either of them is the
  // same one. The help arrives as a node, because a feature never imports another one (§3.1.5).
  const askForATable = (
    <SubmitRequestSection
      type="TableOpen"
      help={
        <p className="text-fg-subtle text-xs">
          {tAdmin('requests.submitHelpHint')} <HelpLink section="basics.requests">{tAdmin('requests.submitHelpLink')}</HelpLink>
        </p>
      }
    />
  )

  return (
    <div className="space-y-4">
      <h1 className="font-serif text-2xl font-semibold">{t('explorer.title')}</h1>
      <SearchQueryInput
        fields={search.fields}
        value={search.value}
        onChange={search.onChange}
        placeholder={t('explorer.searchPlaceholder')}
        label={t('explorer.searchLabel')}
      />
      {isPending && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-40 rounded-lg" />
          ))}
        </div>
      )}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {/* Two different empty states, because they are two different facts. "Nothing is open yet" is
          news about the platform; "nothing matched" is news about what was typed, and telling
          somebody who just searched that masters keep publishing would answer a question they did
          not ask. */}
      {data && tables.length === 0 && search.debouncedQuery.trim() && (
        <EmptyState title={t('explorer.noMatchesTitle')} description={t('explorer.noMatchesDescription')} action={askForATable} />
      )}
      {data && tables.length === 0 && !search.debouncedQuery.trim() && (
        <EmptyState title={t('explorer.emptyTitle')} description={t('explorer.emptyDescription')} action={askForATable} />
      )}
      {tables.length > 0 && (
        <>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {tables.map((table) => (
              <GameTableCard key={table.id} table={table} alreadyApplied={appliedTableIds.has(table.id)} />
            ))}
          </div>
          <LoadMore
            hasMore={hasNextPage}
            isLoading={isFetchingNextPage}
            onLoadMore={() => void fetchNextPage()}
            shown={tables.length}
            total={total}
          />
        </>
      )}
    </div>
  )
}

export { TableListPage as Component }
