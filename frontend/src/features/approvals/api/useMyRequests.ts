import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { approvalsApi } from './approvalsApi'

/**
 * What the reader has asked for, and how each one went.
 *
 * **Every screen that can raise a request reads this first** (#42, principio 2 de
 * `frontend-diseno.md` §1): with a request of that kind already waiting, the button is not offered
 * at all — what is shown instead is that it is pending, and since when. A button whose only possible
 * outcome is a `409` is a button that should not be there.
 *
 * **The query is the caller's**, the same way the tray writes its own `?q=` rather than having the
 * hook decide: the sections ask for `PENDING_REQUESTS_QUERY` because what they need to know is
 * whether something is still waiting, and a hook that silently filtered would be answering a
 * narrower question than its name promises.
 *
 * The listing answers newest first and carries every request, resolved ones included — which is why
 * the filter matters rather than being an optimisation: reading page one of the unfiltered list
 * would miss an old pending request buried under `pageSize.list` answered ones, conclude there was
 * none, and offer the button back. With `/status Pending` the whole answer is at most three rows,
 * one per kind, so one page is provably all of it.
 *
 * **The requester is not part of the query.** The service forces it from the token and joins it with
 * `AND`, so whatever is asked for can only narrow further — there is no spelling of `q` that reaches
 * somebody else's requests.
 *
 * @param query the search query, canonical (`lib/searchQuery.ts`), or undefined for everything
 * @param page  zero-based page number
 * @returns the query for that page of the reader's own requests
 */
export function useMyRequests(query?: string, page = 0) {
  return useQuery({
    queryKey: queryKeys.requests.mine(query, page),
    queryFn: () => approvalsApi.mine(query, page),
    staleTime: staleTime.profile,
  })
}
