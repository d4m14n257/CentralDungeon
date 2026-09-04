/**
 * The public surface of `features/tasks` (#114).
 *
 * From outside, this is the only path: `@/features/tasks`, never a route inside it. What is not
 * exported here is private to the feature — the api module and the query hooks the components use
 * among themselves.
 *
 * Two of the components take **render props** rather than importing what they need: the file picker
 * and the file list belong to `features/files`, and a feature never imports from another. The screens
 * in `src/routes/` are the one place domains are composed (regla dura 16, §3.1.5).
 */
export { ApplicableTaskList } from './components/ApplicableTaskList'
export { MySubmissions } from './components/MySubmissions'
export { TaskAudienceBadge } from './components/TaskAudienceBadge'
export { TaskBoardList } from './components/TaskBoardList'
export { TaskFormDialog, type TaskFormRecipient, type TaskFormSession } from './components/TaskFormDialog'
export { TableTasksSection } from './components/TableTasksSection'
export { TaskStatusBadge } from './components/TaskStatusBadge'
export { TaskSubmissionsPanel } from './components/TaskSubmissionsPanel'
export { TaskSubmitDialog, type PickedFile } from './components/TaskSubmitDialog'

export { useTableTasks } from './api/useTableTasks'
export { useApplicableTasks } from './api/useApplicableTasks'
export { useTaskSubmissions } from './api/useTaskSubmissions'
export { useMySubmissions } from './api/useMySubmissions'
export { usePublishTask } from './api/usePublishTask'
export { useUpdateTask } from './api/useUpdateTask'
export { useCloseTask } from './api/useCloseTask'
export { useSubmitTask } from './api/useSubmitTask'

export { taskFormSchema, type TaskForm } from './schemas'

export type {
  ApplicableTask,
  CreateSubmissionInput,
  CreateTaskInput,
  SubmittedFile,
  TableTask,
  TaskAudience,
  TaskRecipient,
  TaskStatus,
  TaskSubmission,
  TaskSubmissions,
  UpdateTaskInput,
} from './types'
