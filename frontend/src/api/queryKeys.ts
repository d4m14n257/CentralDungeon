/** Central query key factory (arquitectura.md 3.3) - never a loose string literal in a component. */
export const queryKeys = {
  tables: {
    list: (filters?: Record<string, unknown>) => ['tables', 'list', filters] as const,
    /**
     * The whole explorer branch, whatever was searched - what a mutation invalidates when it changes
     * who may see what.
     *
     * **It exists because `list()` stopped being one entry** when the search box arrived (#246): the
     * explorer now keys by what was typed, so invalidating `list()` - which is
     * `['tables', 'list', undefined]` - matches the unfiltered page and misses every filtered one.
     * A mutation never knows what the reader had typed, so it invalidates the branch.
     */
    lists: () => ['tables', 'list'] as const,
    detail: (id: string) => ['tables', 'detail', id] as const,
    managedDetail: (id: string) => ['tables', 'managed-detail', id] as const,
    mine: () => ['tables', 'mine'] as const,
    /**
     * `/player/history` (#133): closed tables, accumulated with "See more" like the explorer - no
     * page number in the key, `useInfiniteQuery` keeps the pages already fetched itself. Its own
     * branch and not `mine()` with a filter tacked on: `mine` answers "what am I playing now" and
     * this answers "how did it go" - two different questions whose answers must never share a cache
     * entry, the same reasoning as `schedule.mine()` above having its own branch instead of living
     * inside `tables`.
     */
    history: () => ['tables', 'history'] as const,
    managed: () => ['tables', 'managed'] as const,
    /**
     * The `/admin/tables` listing, keyed by what was searched, which statuses were asked for and
     * which page - the same `admin` convention `files`, `catalogs`, `users` and `requests` follow,
     * with `files.admin` as the exact shape.
     *
     * **It gained `query` with F3.3** (#176): the listing now shows every table there is and accepts
     * the six search commands, so keying only by status would serve one search's answer to another.
     */
    admin: (query?: string, statuses?: string[], page = 0) => ['tables', 'admin', query, statuses, page] as const,
    /**
     * The whole admin branch - what approving, requesting changes, assigning masters, creating and
     * deleting all invalidate.
     *
     * It exists for the same reason `users.adminAll()` and `requests.adminAll()` do: a mutation never
     * knows what the reader had typed or which page they were on, and a resolution moves a table
     * between statuses the current filter may or may not include, so patching one entry would leave
     * the rest of the cache describing a state that no longer is.
     */
    adminAll: () => ['tables', 'admin'] as const,
    statusHistory: (id: string) => ['tables', 'status-history', id] as const,
    /** The platform's table types. One list for the whole app - admins change it rarely (#72). */
    types: () => ['tables', 'types'] as const,
  },
  /**
   * The reader's own week (#227). Its own branch and not part of `tables`: it is an answer about a
   * person across everything they run *and* everything they play at, so every mutation that moves an
   * agenda invalidates it, whichever side it came from.
   */
  schedule: {
    mine: () => ['schedule', 'mine'] as const,
  },
  master: {
    /**
     * The master's work tray (#136). Its own branch and not part of `tables`: it is an answer about
     * a person across every table they run, and every mutation that resolves work invalidates it.
     */
    dashboard: () => ['master', 'dashboard'] as const,
  },
  /**
   * The shared admin tray (#100, F3.3). Its own branch and not a corner of `tables` or `requests`:
   * it is one answer merged out of several sources, and what takes a row out of it is a mutation on
   * whichever aggregate the row came from - so every one of those invalidates `all()` as well as its
   * own branch.
   *
   * **No `query` in the key, deliberately.** The tray has no search (§2 del contrato): a work list
   * orders itself by age and empties, and a box over it would be solving the wrong problem.
   * `/admin/tables` is the screen that searches.
   */
  adminQueue: {
    /** One page of the tray, oldest first. */
    list: (page = 0) => ['adminQueue', 'list', page] as const,
    /** The whole tray - what claiming, releasing and every resolution invalidate. */
    all: () => ['adminQueue'] as const,
  },
  sessions: {
    /** A table's calendar, as the people running it see it. The whole list, never paginated. */
    list: (tableId: string) => ['sessions', 'list', tableId] as const,
    /**
     * The player's own calendar and attendance. Its own branch rather than a filter over `list`:
     * they are two different answers from the server, and whoever reads one can rarely read the other.
     */
    mine: (tableId: string) => ['sessions', 'mine', tableId] as const,
  },
  files: {
    /** The reuse history of #65, keyed by what the picker searched and how /my/files narrowed it. */
    mine: (query?: string, category?: string, page = 0) => ['files', 'mine', query, category, page] as const,
    /** What the platform published, by cajón (#233, which derogated the audience of #64). */
    public: (category?: string) => ['files', 'public', category] as const,
    /**
     * One table's attachments, as the people running it see them. Its own branch and not part of
     * `tables.detail`: what a master sees includes the private ones, and the table's detail carries
     * only what it shares - two different answers that must not share a cache entry (#79).
     */
    table: (tableId: string) => ['files', 'table', tableId] as const,
    /** The /admin/files table. Every admin mutation invalidates this branch and nothing else. */
    admin: (query?: string, statuses?: string[], fileTypes?: string[], category?: string, page = 0) =>
      ['files', 'admin', query, statuses, fileTypes, category, page] as const,
  },
  tasks: {
    /** One table's board, as the people running it see it. The whole list, never paginated. */
    table: (tableId: string) => ['tasks', 'table', tableId] as const,
    /**
     * What the table asks of **this** reader. Its own branch and not part of `tables.detail`:
     * the answer depends on who is asking — whether they play there, and who a `Single` task names —
     * so two people looking at the same table must not share one cache entry (#121).
     */
    applicable: (tableId: string) => ['tasks', 'applicable', tableId] as const,
    /** Everything handed in to one task, plus who has not answered. Only the masters read it. */
    submissions: (taskId: string) => ['tasks', 'submissions', taskId] as const,
    /** My own answers to one task. A list, because answers accumulate (#76). */
    mine: (taskId: string) => ['tasks', 'mine', taskId] as const,
  },
  catalogs: {
    /** The whole branch. What every catalog mutation invalidates: one admin action can move rows
     *  that are not on screen - a merge repoints a group, a disable hands it to a successor - so
     *  patching one entry would leave the rest of the cache describing a state that no longer is. */
    all: () => ['catalogs'] as const,
    /** The accepted values a combobox offers. Keyed by catalog and by what was typed. */
    list: (kind: string, query?: string) => ['catalogs', 'list', kind, query] as const,
    /** One value by id, whatever its status - how a pending proposal is read back (#57). */
    detail: (kind: string, id: string) => ['catalogs', 'detail', kind, id] as const,
    /** One value's whole synonym group - what the merge and disable dialogs are built on. */
    group: (kind: string, id: string) => ['catalogs', 'group', kind, id] as const,
    /** The /admin/catalogs table. Every admin mutation invalidates this branch and nothing else. */
    admin: (kind: string, query?: string, statuses?: string[], page = 0) => ['catalogs', 'admin', kind, query, statuses, page] as const,
  },
  registrations: {
    candidates: (tableId: string) => ['registrations', 'candidates', tableId] as const,
    mine: () => ['registrations', 'mine'] as const,
    /** A table's roster. Its own branch: the candidate queue's order is a rule (#28), a roster's is not. */
    players: (tableId: string) => ['registrations', 'players', tableId] as const,
  },
  notifications: {
    list: () => ['notifications', 'list'] as const,
  },
  /**
   * The request mechanism (#42): one branch for the four questions it answers.
   *
   * `mine` and `admin` are two different answers to two different audiences and never share an
   * entry: what somebody asked for is theirs, and the tray is every request there is. The same
   * `admin` convention `users`, `tables`, `files` and `catalogs` already use.
   */
  requests: {
    /**
     * What the reader asked for, and how each one went - what tells a screen not to offer the button
     * again. Keyed by what was searched, like `admin` below: the sections ask for the pending ones
     * alone, and a filtered answer must never be served as if it were the whole list.
     */
    mine: (query?: string, page = 0) => ['requests', 'mine', query ?? '', page] as const,
    /** The whole of the reader's own branch - what submitting a request invalidates. */
    mineAll: () => ['requests', 'mine'] as const,
    /** The /admin/requests tray, keyed by what was searched and which page. */
    admin: (query?: string, page = 0) => ['requests', 'admin', query ?? '', page] as const,
    /**
     * The whole admin branch - what approving and rejecting invalidate.
     *
     * It exists for the same reason `users.adminAll()` does: a mutation never knows what the reader
     * had typed or which page they were on, and a resolution takes a row out of the pending filter
     * entirely, so patching a single entry would leave the rest of the cache describing a state that
     * no longer is.
     */
    adminAll: () => ['requests', 'admin'] as const,
    /** One request in full - where the resolution is: who sealed it, when, and why. */
    adminDetail: (id: string) => ['requests', 'admin', 'detail', id] as const,
    /**
     * The veto requests pending on one table (F3.4, #39).
     *
     * **Its own branch, and deliberately outside `admin`**: a `PlayerBan` is not the platform's work
     * but the table's, resolved by its `Primary` and excluded from the shared tray. Keying it under
     * `admin` would put a master's list inside the branch every admin mutation invalidates, and the
     * two audiences never read the same rows.
     */
    banRequests: (tableId: string) => ['requests', 'ban', tableId] as const,
  },
  users: {
    me: () => ['users', 'me'] as const,
    /** The picker's results. Keyed by scope too: the admin directory and a table's candidate
     *  search are different answers to the same words, and must not share a cache entry. */
    search: (query: string, tableId?: string) => ['users', 'search', tableId ?? 'all', query] as const,
    /**
     * The /admin/users table (F3.1). The same `admin` convention `tables`, `files` and `catalogs`
     * already use, so every mutation on the screen invalidates the branch `['users', 'admin']` and
     * nothing outside it - `me()` and `search()` answer different questions to different callers.
     */
    admin: (query?: string, page = 0) => ['users', 'admin', query ?? '', page] as const,
    /**
     * The whole admin branch - what the four mutators invalidate.
     *
     * It exists for the same reason `tables.lists()` and `catalogs.all()` do: a mutation never knows
     * what the reader had typed or which page they were on, and one role change moves rows that are
     * not on screen, so patching a single entry would leave the rest of the cache describing a state
     * that no longer is.
     */
    adminAll: () => ['users', 'admin'] as const,
    /** One account as an admin sees it - what the six mutators answer with, written back by id. */
    adminDetail: (id: string) => ['users', 'admin', 'detail', id] as const,
    /** What admins did to one account, and why (#84, #169). Invalidated by every one of the four
     *  mutators: each of them is what adds a row to it. */
    adminHistory: (id: string) => ['users', 'admin', 'history', id] as const,
  },
  /**
   * Profile screens (#248): `/player/profile` and `/player/users/:id`. Its own branch and not part
   * of `users.me()`: that key backs the app shell (contexts, onboarding, `hasManagedTables`) and
   * never carries attendance, while this one answers a visibility-gated question (#41, #44, #47)
   * that can 404 on purpose (#249) — mixing the two would risk a stale shell surviving a profile
   * that just stopped being visible, or the reverse.
   */
  profiles: {
    /** The reader's own profile. Its own key rather than `detail(myId)`: the reader may not know
     *  their own id offhand, and `/me/profile` never needs one to ask. */
    mine: () => ['profiles', 'mine'] as const,
    /** Somebody else's profile, keyed by id - visibility can change per viewer, so two people
     *  looking at the same id must not share a cache entry either (#121). */
    detail: (id: string) => ['profiles', 'detail', id] as const,
  },
  system: {
    health: () => ['system', 'health'] as const,
    /**
     * The platform limits every client mirrors (F3.5, #141). Under `system` and not under a domain:
     * the answer is about the platform, is the same for everybody, and is read by whichever screen
     * is about to state a rule — which is why it is cached for an hour and invalidated by the one
     * screen that can move it.
     */
    limits: () => ['system', 'limits'] as const,
  },
  /**
   * The editable configuration of #141. Its own branch and not a corner of `system`: `system` holds
   * answers anybody reads, and this is the administrative view — what is configured, by whom, and
   * when — which only Admin and Owner can ask for.
   */
  settings: {
    /** Every setting, override or default. What editing one invalidates. */
    list: () => ['settings', 'list'] as const,
    /** What was done to one setting, and why. Invalidated by the edit that adds a row to it. */
    history: (key: string) => ['settings', 'history', key] as const,
  },
} as const
