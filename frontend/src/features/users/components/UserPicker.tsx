import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { ErrorState } from '@/components/ErrorState'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Skeleton } from '@/components/ui/skeleton'
import { useDebounce } from '@/hooks/useDebounce'
import { buildSearchQuery, emptySearchQuery, type SearchField } from '@/lib/searchQuery'
import { cn } from '@/lib/utils'

import { useUserSearch } from '../api/useUserSearch'
import type { UserSummary } from '../types'

interface UserPickerProps {
  onSelect: (user: UserSummary) => void
  /** Who not to offer: normally the people already picked. */
  excludedIds?: readonly string[]
  /**
   * Search among the people who could be made a master of this table, instead of the whole
   * directory. An id and never an entity (§3.1.5), so this component still knows nothing about
   * tables — what changes is which endpoint answers, because the two are authorized differently
   * (#165).
   */
  tableId?: string
}

/**
 * Finding a person and picking them. The basic criterion is the Discord name **or** the one in the
 * system: whoever is searching knows one of the two, not which of them is stored where
 * (decisiones.md #164).
 */
export function UserPicker({ onSelect, excludedIds = [], tableId }: UserPickerProps) {
  const { t } = useTranslation('users')
  const [query, setQuery] = useState(emptySearchQuery)

  const fields = useMemo<SearchField[]>(
    () => [
      { name: 'discord_name', label: t('search.discordName') },
      { name: 'user_name', label: t('search.userName') },
    ],
    [t],
  )

  // 400 ms: the search goes out when typing stops, not once per keystroke (decisiones.md #164).
  const rawQuery = buildSearchQuery(query)
  const debouncedQuery = useDebounce(rawQuery, 400)
  const hasQuery = debouncedQuery.trim().length > 0
  const { data, isFetching, isLoadingError, refetch } = useUserSearch(debouncedQuery, hasQuery, tableId)
  /** While the debounce runs, what is on screen is the previous search: it is dimmed so it does not lie. */
  const isStale = isFetching || rawQuery.trim() !== debouncedQuery.trim()

  const results = (data?.content ?? []).filter((user) => !excludedIds.includes(user.id))

  return (
    <div className="space-y-2">
      <SearchQueryInput fields={fields} value={query} onChange={setQuery} label={t('search.label')} placeholder={t('search.placeholder')} />
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {!isLoadingError && !hasQuery && <p className="text-fg-subtle text-sm">{t('search.startTyping')}</p>}
      {!isLoadingError && hasQuery && !data && <Skeleton className="h-24 w-full" />}
      {!isLoadingError && hasQuery && data && results.length === 0 && <p className="text-fg-muted text-sm">{t('search.empty')}</p>}
      {!isLoadingError && results.length > 0 && (
        <ul className={cn('border-border divide-border max-h-56 divide-y overflow-y-auto rounded-md border', isStale && 'opacity-60')}>
          {results.map((user) => (
            <li key={user.id}>
              <button
                type="button"
                onClick={() => onSelect(user)}
                className="hover:bg-raised flex w-full flex-col items-start gap-0.5 px-3 py-2 text-left"
              >
                <span className="text-sm font-medium">{user.discordUsername}</span>
                <span className="text-fg-muted text-xs">{user.name ?? t('search.noName')}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
