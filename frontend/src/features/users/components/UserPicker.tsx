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
  /** A quién no ofrecer: normalmente los que ya se eligieron. */
  excludedIds?: readonly string[]
}

/**
 * Buscar una persona y elegirla. El criterio básico es el nombre de Discord **o** el del sistema:
 * quien busca sabe uno de los dos, no cuál guarda el sistema dónde (decisiones.md #164).
 */
export function UserPicker({ onSelect, excludedIds = [] }: UserPickerProps) {
  const { t } = useTranslation('users')
  const [query, setQuery] = useState(emptySearchQuery)

  const fields = useMemo<SearchField[]>(
    () => [
      { name: 'discord_name', label: t('search.discordName') },
      { name: 'user_name', label: t('search.userName') },
    ],
    [t],
  )

  // 400 ms: la búsqueda sale cuando se deja de escribir, no una por tecla (decisiones.md #164).
  const rawQuery = buildSearchQuery(query)
  const debouncedQuery = useDebounce(rawQuery, 400)
  const hasQuery = debouncedQuery.trim().length > 0
  const { data, isFetching, isLoadingError, refetch } = useUserSearch(debouncedQuery, hasQuery)
  /** Mientras corre el debounce lo que se ve es la búsqueda anterior: se atenúa para no mentir. */
  const isStale = isFetching || rawQuery.trim() !== debouncedQuery.trim()

  const results = (data?.content ?? []).filter((user) => !excludedIds.includes(user.id))

  return (
    <div className="space-y-2">
      <SearchQueryInput
        fields={fields}
        value={query}
        onChange={setQuery}
        label={t('search.label')}
        placeholder={t('search.placeholder')}
      />
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
