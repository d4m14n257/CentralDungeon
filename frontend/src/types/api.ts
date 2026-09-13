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
  /**
   * The backend's own words, in English and for a log (#197). **Never shown to a person**: what they
   * read is rendered here from `errorCode` and `errorParams`, in the language they chose.
   */
  detail: string
  errorCode: string
  /**
   * The values the translated message needs, keyed by placeholder name. Absent for the errors whose
   * message has none, which is most of them.
   */
  errorParams?: Record<string, string>
}

/**
 * Mirror of the backend's `AttendanceSummaryResponse` (#137): somebody's historical attendance,
 * as three counts and the denominator that goes with them.
 *
 * Lives here and not in `features/tables/types.ts` because a second feature needs the exact same
 * shape unchanged: `features/tables` uses it for one table's sessions, `features/users` for a
 * profile's aggregate across every table (arquitectura.md 3.1.2 - it moves up the moment a second
 * real consumer needs it, and moving it up is what lets `AttendanceSummaryView` stay the one place
 * that renders it instead of being copied into a second feature).
 */
export interface AttendanceSummary {
  present: number
  excused: number
  absent: number
  registered: number
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
