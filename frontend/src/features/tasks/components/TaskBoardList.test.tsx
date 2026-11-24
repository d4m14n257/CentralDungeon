import { render, screen, within } from '@testing-library/react'
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

  it('splits the board into candidates and players, with the individual request among the players', () => {
    render(
      <TaskBoardList
        tasks={[
          task({ taskId: 'c', title: 'Cuéntanos por qué', audience: 'Candidates' }),
          task({ taskId: 'p', title: 'Sube tu ficha', audience: 'Players' }),
          task({ taskId: 's', title: 'Revisa tu trasfondo', audience: 'Single', targetUserId: 'user-7', targetUserName: 'Carla' }),
        ]}
        isBusy={false}
        renderFiles={() => null}
      />,
    )

    const candidates = within(screen.getByRole('region', { name: 'Candidatos' }))
    const players = within(screen.getByRole('region', { name: 'Jugadores' }))

    expect(candidates.getByRole('button', { name: /Cuéntanos por qué/ })).toBeTruthy()
    expect(candidates.queryByRole('button', { name: /Sube tu ficha/ })).toBeNull()
    expect(players.getByRole('button', { name: /Sube tu ficha/ })).toBeTruthy()
    expect(players.getByRole('button', { name: /Revisa tu trasfondo/ })).toBeTruthy()
  })

  it('says a section is empty on its own', () => {
    render(<TaskBoardList tasks={[task({ audience: 'Candidates' })]} isBusy={false} renderFiles={() => null} />)

    expect(within(screen.getByRole('region', { name: 'Jugadores' })).getByText('Nada pedido a los jugadores')).toBeTruthy()
    expect(within(screen.getByRole('region', { name: 'Candidatos' })).queryByText('Nada pedido a los candidatos')).toBeNull()
  })

  it('shows everything about a request once its row is expanded', async () => {
    render(
      <TaskBoardList
        tasks={[
          task({
            description: '<p>Trae la ficha firmada</p>',
            dueAt: '2026-12-01T18:00:00',
            files: [{ fileId: 'f-1', name: 'plantilla.pdf', mimeType: 'application/pdf', sizeBytes: 10 }],
          }),
        ]}
        isBusy={false}
        renderFiles={(files) => <span>{files.map((file) => file.name).join(', ')}</span>}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: /Sube tu ficha/ }))

    expect(screen.getByText('Trae la ficha firmada')).toBeTruthy()
    expect(screen.getByText('Fecha sugerida:')).toBeTruthy()
    expect(screen.getByText('plantilla.pdf')).toBeTruthy()
    expect(screen.getByText('Entregaron 1 de 2')).toBeTruthy()
  })

  it('offers to edit only when the reader may change the request', () => {
    const { rerender } = render(<TaskBoardList tasks={[task()]} isBusy={false} renderFiles={() => null} />)
    expect(screen.queryByRole('button', { name: 'Editar la petición' })).toBeNull()

    rerender(<TaskBoardList tasks={[task()]} isBusy={false} renderFiles={() => null} onEdit={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Editar la petición' })).toBeTruthy()
  })

  it('does not offer to edit a closed request, which can still be read', () => {
    render(<TaskBoardList tasks={[task({ status: 'Closed' })]} isBusy={false} renderFiles={() => null} onEdit={vi.fn()} />)

    expect(screen.queryByRole('button', { name: 'Editar la petición' })).toBeNull()
  })
})
