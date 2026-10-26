import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { NotFoundState } from '@/components/NotFoundState'
import { ApiError } from '@/types/api'

interface QueryErrorStateProps {
  /** What the failed query threw. */
  error: unknown
  /** Tries the request again. Only offered when trying again can change the answer. */
  onRetry?: () => void
}

/**
 * The state a screen paints when its read failed, picked from what the failure means.
 *
 * A 403 is a refusal ({@link ForbiddenState}) and a 404 is an absence ({@link NotFoundState}): neither
 * is helped by retrying, and reading them as "something went wrong" sends the person back to a button
 * that cannot work. Anything else is a failure worth another attempt, so it gets
 * {@link ErrorState} with the retry.
 *
 * @param props.error   what the query threw
 * @param props.onRetry the query's refetch
 */
export function QueryErrorState({ error, onRetry }: QueryErrorStateProps) {
  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }
  if (error instanceof ApiError && error.status === 404) {
    return <NotFoundState />
  }
  return <ErrorState {...(onRetry ? { onRetry } : {})} />
}
