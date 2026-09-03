/**
 * Mirror of the backend's pagination envelope. Instantiated per item type
 * (`PageResponse<GameTableSummary>`), never re-declared per feature (#3.2 regla 8).
 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Mirrors the backend's RFC 9457 body (arquitectura.md 2.5) - never a bare string, never a 418. */
export interface ProblemDetail {
  title: string
  status: number
  detail: string
  errorCode: string
}

/**
 * What `api/client.ts` throws when the backend refuses a call. It carries the whole `ProblemDetail`,
 * so a screen can branch on `errorCode` or on the status instead of matching on a message string.
 *
 * A failure that is *not* an `ApiError` means the request never got an answer at all - the backend
 * is unreachable - which is a different message to the user.
 */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail)
    this.status = status
    this.problem = problem
  }
}
