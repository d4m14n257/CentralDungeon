import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { TableTask, TaskSubmissions } from '../types'
import { TaskBoardList } from './TaskBoardList'

const submissions: TaskSubmissions = {
  taskId: 'task-1',
  submissions: [{ submissionId: 's-1', taskId: 'task-1', userId: 'user-1', userName: 'Ana', content: null, files: [], submittedAt: null }],
  missing: [{ userId: 'user-2', userName: 'Beto' }],
  recipientCount: 2,
}

vi.mock('../api/useTaskSubmissions', () => ({
  useTaskSubmissions: () => ({ data: submissions, isPending: false, isLoadingError: false }),
}))

function task(overrides: Partial<TableTask> = {}): TableTask {
  return {
    taskId: 'task-1',
    gameTableId: 'table-1',
    tableSessionId: null,
    sessionSequenceNumber: null,
    audience: 'Players',
    targetUserId: null,
    targetUserName: null,
    title: 'Sube tu ficha',
    description: null,
    acceptsText: true,
    acceptsFiles: false,
    isMandatory: false,
    files: [],
    dueAt: null,
    status: 'Open',
    submissionCount: 1,
    respondentCount: 1,
    recipientCount: 2,
    createdAt: '2026-10-01T10:00:00',
    ...overrides,
  }
}

describe('TaskBoardList', () => {
  const onOpenPerson = vi.fn()

  beforeEach(() => onOpenPerson.mockReset())

  it('opens the card of the one person an individual request is addressed to', async () => {
    render(
      <TaskBoardList
        tasks={[task({ audience: 'Single', targetUserId: 'user-7', targetUserName: 'Carla' })]}
        isBusy={false}
        renderFiles={() => null}
        onOpenPerson={onOpenPerson}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Ver la ficha de Carla' }))

    expect(onOpenPerson).toHaveBeenCalledWith('user-7')
  })

  it('opens the card of whoever answered and of whoever is still missing', async () => {
    render(<TaskBoardList tasks={[task()]} isBusy={false} renderFiles={() => null} onOpenPerson={onOpenPerson} />)
    await userEvent.click(screen.getByRole('button', { name: /Sube tu ficha/ }))

    await userEvent.click(screen.getByRole('button', { name: 'Ver la ficha de Ana' }))
    await userEvent.click(screen.getByRole('button', { name: 'Ver la ficha de Beto' }))

    expect(onOpenPerson).toHaveBeenNthCalledWith(1, 'user-1')
    expect(onOpenPerson).toHaveBeenNthCalledWith(2, 'user-2')
  })

  it('draws no card action without a way to open a person', async () => {
    render(<TaskBoardList tasks={[task()]} isBusy={false} renderFiles={() => null} />)
    await userEvent.click(screen.getByRole('button', { name: /Sube tu ficha/ }))

    expect(screen.queryByRole('button', { name: /Ver la ficha de/ })).toBeNull()
  })
})
