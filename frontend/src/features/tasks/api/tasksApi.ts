import { api } from '@/api/client'

import type {
  ApplicableTask,
  CreateSubmissionInput,
  CreateTaskInput,
  TableTask,
  TaskSubmission,
  TaskSubmissions,
  UpdateTaskInput,
} from '../types'

/**
 * Every call about what a table asks, split the way the backend splits them: a table's board under
 * `/api/v1/game-tables/{id}/tasks`, and acting on one task under `/api/v1/tasks/{id}`.
 *
 * **`applicable` is its own call and not a field of the table's detail**, unlike the calendar (F1.3)
 * and the shared files (F1.4). Those are the same for everybody who may see the table; this list
 * depends on the reader — whether they play there, and who a `Single` task names — so it is asked for
 * with the reader's own token and never mixed into an answer that is also served without one (#121).
 */
export const tasksApi = {
  /**
   * The table's whole board, as the people running it see it.
   *
   * @param tableId the table
   */
  listForTable: (tableId: string) => api.get<TableTask[]>(`/api/v1/game-tables/${tableId}/tasks`),

  /**
   * What this table is asking of **me** — the read-only list on `/tables/:id` and `/my/tables/:id`.
   *
   * Somebody who has not applied still gets the `Candidates` ones: what will be asked of you is half
   * of deciding whether to apply (#206).
   *
   * @param tableId the table
   */
  listApplicable: (tableId: string) => api.get<ApplicableTask[]>(`/api/v1/game-tables/${tableId}/tasks/applicable`),

  /**
   * Publishes a task, which is also what notifies the people it is addressed to (#77).
   *
   * @param tableId the table doing the asking
   * @param input   what is being asked, of whom
   */
  publish: (tableId: string, input: CreateTaskInput) => api.post<TableTask, CreateTaskInput>(`/api/v1/game-tables/${tableId}/tasks`, input),

  /**
   * Corrects a task. It does not notify again — #77 puts the notification at publication.
   *
   * @param taskId the task
   * @param input  the whole state it should end in (#189)
   */
  update: (taskId: string, input: UpdateTaskInput) => api.patch<TableTask, UpdateTaskInput>(`/api/v1/tasks/${taskId}`, input),

  /**
   * Stops the task from taking answers. What came in stays readable (#76).
   *
   * @param taskId the task
   */
  close: (taskId: string) => api.post<TableTask>(`/api/v1/tasks/${taskId}/close`),

  /**
   * What came in and who has not answered, for the people running the table.
   *
   * @param taskId the task
   */
  listSubmissions: (taskId: string) => api.get<TaskSubmissions>(`/api/v1/tasks/${taskId}/submissions`),

  /**
   * The actor's own answers to a task.
   *
   * @param taskId the task
   */
  listMySubmissions: (taskId: string) => api.get<TaskSubmission[]>(`/api/v1/tasks/${taskId}/submissions/mine`),

  /**
   * Hands in an answer. **Every call inserts a new one** (#76): a second version leaves the first
   * exactly where it was, which is why this is a POST to a collection and not a PUT on a resource.
   *
   * @param taskId the task being answered
   * @param input  the written answer, the files, or both
   */
  submit: (taskId: string, input: CreateSubmissionInput) =>
    api.post<TaskSubmission, CreateSubmissionInput>(`/api/v1/tasks/${taskId}/submissions`, input),
}
