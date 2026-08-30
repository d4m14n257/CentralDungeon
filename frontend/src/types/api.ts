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

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail)
    this.status = status
    this.problem = problem
  }
}
