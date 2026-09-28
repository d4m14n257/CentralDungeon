import { useState } from 'react'

import { buildSearchQuery, searchQueryOf, type SearchField, type SearchQueryValue } from '@/lib/searchQuery'

/** What a screen has to say to get a search box: which commands, and where the query goes. */
export interface UseSearchQueryOptions {
  /** The commands this box accepts, in the order it offers them. */
  fields: readonly SearchField[]
  /** The canonical query to start from — `?q=` restored from the URL, normally (#185). */
  initialQuery?: string
  /**
   * Called with the canonical query **only when a search changes it** — the second Enter, or «clear
   * filters» (#268).
   *
   * This is the URL write. Editing the chips does not reach it: nothing is searched until it is
   * confirmed, and a screen whose URL writer also resets the page would otherwise send the reader
   * back to page one on every keystroke without the results having changed at all.
   */
  onQueryChange?: (query: string) => void
}

/** Everything a screen needs to render a {@link SearchQueryInput} and ask the server for its results. */
export interface UseSearchQueryResult {
  /** Spread straight onto the box: it takes its commands, its state and its setter from here. */
  fields: readonly SearchField[]
  /** The box's state: its chips, what is being typed and the connector waiting for the next one. */
  value: SearchQueryValue
  /** The box's setter. Editing is not searching. */
  onChange: (value: SearchQueryValue) => void
  /**
   * The canonical query last confirmed with Enter: what the server should be asked for and what the
   * URL should say. Never the chips still being composed, nor what is being typed (#268).
   */
  query: string
  /** The box's way of confirming a search; hand it over as `onSearch`, with `query` as `searchedQuery`. */
  onSearch: (query: string) => void
}

/**
 * The wiring every search box needs, written once (#240).
 *
 * Three screens had grown their own copy of the same six lines — hold the structured value, turn it
 * into the canonical string and write it to the URL — and they had already started to drift: one of
 * them parsed the URL against a **second** hardcoded list of command names, kept beside the one it
 * passed to the box and free to disagree with it. Here there is one list, the one the box is given.
 *
 * There is no debounce any more (#268): the query only moves when somebody confirms it — Enter on an
 * empty text, after the Enter that closed the chips — or clears the filters. One request per intention.
 *
 * It holds no domain and no endpoint: which commands exist and who answers them are the caller's,
 * exactly as in the box itself (arquitectura §3.1.1).
 */
export function useSearchQuery({ fields, initialQuery = '', onQueryChange }: UseSearchQueryOptions): UseSearchQueryResult {
  // Read once, on purpose: from here on the box owns the state, and re-reading the URL it is itself
  // writing would fight with whatever is half-typed.
  const [value, setValue] = useState<SearchQueryValue>(() => searchQueryOf(initialQuery, fields))

  // Its own state and not derived from `value`: the chips on screen and the search the results answer
  // part company between the Enter that closes a criterion and the one that confirms it.
  const [query, setQuery] = useState(() => buildSearchQuery(value))

  function onSearch(next: string) {
    if (next === query) return
    setQuery(next)
    onQueryChange?.(next)
  }

  return { fields, value, onChange: setValue, query, onSearch }
}
