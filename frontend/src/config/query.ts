import { MutationCache, QueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import i18n from '@/providers/i18n'
import { ApiError } from '@/types/api'

/** Per-data staleTime policy (arquitectura.md 3.3) - the default of 0 is what causes request goteo. */
export const staleTime = {
  catalogs: 60 * 60 * 1000,
  tableList: 30_000,
  tableDetail: 60_000,
  notifications: Infinity,
  profile: 5 * 60 * 1000,
  /** A person's own files and a table's attachments: they change when somebody acts, not on their own. */
  files: 60_000,
  /** What a table asks and what came in: same shape as files — somebody publishes, somebody answers. */
  tasks: 60_000,
} as const

/**
 * The error codes that have a message of their own to show, rendered here from the code and the
 * parameters the backend sent with it (#197).
 *
 * A short list on purpose: a person only reads a specific message when the backend committed to a
 * code for it. `SCHEDULE_CONFLICT` (#178) is the first — a clash is refused naming the table it
 * collides with, and answering "could not save" there would hide the one thing the person can
 * actually resolve (principio 2 de frontend-diseno.md §1).
 *
 * Before #197 the backend's own `detail` was shown verbatim, which meant a Spanish sentence no
 * matter what language the reader had picked.
 */
const EXPLAINED_ERROR_CODES = new Set([
  'SCHEDULE_CONFLICT',
  // Every refusal of an upload (F1.4). "Could not save" would be the worst possible answer here:
  // the person picked a file, and what they need to know is which file and why - the type, or the
  // size and the limit. The limit travels as a number of bytes and the sentence is written on this
  // side, in their language and their units (#197).
  'FILE_TOO_LARGE',
  'FILE_TYPE_NOT_ALLOWED',
  'FILE_EMPTY',
  // Handing in an answer to a request the master already closed (F1.5). "Could not save" is the
  // worst possible answer to somebody who just wrote one: retrying will not help, and what they need
  // to know is that the intake ended — which is a fact about the table, not about their connection.
  'TASK_CLOSED',
])

/**
 * The safety net for every mutation that does not bring its own `onError`, which today is all of
 * them: a write that fails - backend down, a 500, whatever it is - used to say nothing at all. The
 * button simply stopped being "pending" and that was the end of it, with whoever pressed it none the
 * wiser. Queries do not come through here: their four mandatory states (frontend-diseno.md 5)
 * already show the error on the screen itself, and repeating it here would be the same notice twice.
 *
 * A particular mutation that needs a more specific message adds its own `onError` on the
 * `useMutation` - React Query calls both, it does not replace this one.
 */
function reportMutationError(error: unknown) {
  if (!(error instanceof ApiError)) {
    toast.error(i18n.t('errors.offline'))
    return
  }
  if (!EXPLAINED_ERROR_CODES.has(error.problem.errorCode)) {
    toast.error(i18n.t('errors.mutationFailed'))
    return
  }
  toast.error(
    i18n.t(`errors.codes.${error.problem.errorCode}`, { ...error.problem.errorParams, defaultValue: i18n.t('errors.mutationFailed') }),
  )
}

/**
 * The application's single QueryClient.
 *
 * Two decisions live in it. Queries get an explicit `staleTime` instead of the library's default of
 * 0, which is what produces the steady drip of refetches; and every mutation without its own
 * `onError` gets a toast, so a failed write never ends in silence.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: staleTime.tableList,
      retry: 1,
    },
  },
  mutationCache: new MutationCache({
    onError: reportMutationError,
  }),
})
