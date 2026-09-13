import { useState } from 'react'

import { buildSearchQuery, searchQueryOf, type SearchField, type SearchQueryValue } from '@/lib/searchQuery'

import { useDebounce } from './useDebounce'

/** What a screen has to say to get a search box: which commands, and where the query goes. */
export interface UseSearchQueryOptions {
  /** The commands this box accepts, in the order it offers them. */
  fields: readonly SearchField[]
  /** The canonical query to start from — `?q=` restored from the URL, normally (#185). */
  initialQuery?: string
  /**
   * Called with the canonical query on every change, before the debounce.
   *
   * This is the URL write, and it is deliberately **not** debounced: what the address bar says has to
   * match what the box says, and lagging it by 400 ms means a refresh can lose the last thing typed.
   * The debounce is for the server, and it lives on {@link UseSearchQueryResult.debouncedQuery}.
   */
  onQueryChange?: (query: string) => void
  /** How long the server waits after the last keystroke. 400 ms, as everywhere (#164). */
  delay?: number
}

/** Everything a screen needs to render a {@link SearchQueryInput} and ask the server for its results. */
export interface UseSearchQueryResult {
  /** Spread straight onto the box: it takes its commands, its state and its setter from here. */
  fields: readonly SearchField[]
  value: SearchQueryValue
  onChange: (value: SearchQueryValue) => void
  /** What is on screen right now, canonical. What the URL should say. */
  query: string
  /** The same, once typing stops. What the server should be asked for. */
  debouncedQuery: string
  /** Whether what is on screen is still ahead of what was asked for, so a stale list can say so. */
  isStale: boolean
}

/**
 * The wiring every search box needs, written once (#240).
 *
 * Three screens had grown their own copy of the same six lines — hold the structured value, turn it
 * into the canonical string, debounce that string, and write it to the URL — and they had already
 * started to drift: one of them parsed the URL against a **second** hardcoded list of command names,
 * kept beside the one it passed to the box and free to disagree with it. Here there is one list, the
 * one the box is given.
 *
 * It holds no domain and no endpoint: which commands exist and who answers them are the caller's,
 * exactly as in the box itself (arquitectura.md 3.1.1).
 */
export function useSearchQuery({ fields, initialQuery = '', onQueryChange, delay = 400 }: UseSearchQueryOptions): UseSearchQueryResult {
  // Read once, on purpose: from here on the box owns the state, and re-reading the URL it is itself
  // writing would fight with whatever is half-typed.
  const [value, setValue] = useState<SearchQueryValue>(() => searchQueryOf(initialQuery, fields))

  const query = buildSearchQuery(value, fields)
  const debouncedQuery = useDebounce(query, delay)

  function onChange(next: SearchQueryValue) {
    setValue(next)
    onQueryChange?.(buildSearchQuery(next, fields))
  }

  return { fields, value, onChange, query, debouncedQuery, isStale: query.trim() !== debouncedQuery.trim() }
}
