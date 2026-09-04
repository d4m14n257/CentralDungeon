import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ApplicableTaskList } from './ApplicableTaskList'
import type { ApplicableTask } from '../types'

const get = vi.hoisted(() => vi.fn())

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { ...actual.api, get } }
})

function wrap(children: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

function task(overrides: Partial<ApplicableTask> = {}): ApplicableTask {
  return {
    taskId: 'task-1',
    audience: 'Players',
    tableSessionId: null,
    sessionSequenceNumber: null,
    title: 'Ficha de personaje',
    description: null,
    acceptsText: true,
    acceptsFiles: true,
    isMandatory: false,
    dueAt: null,
    canSubmit: true,
    mySubmissionCount: 0,
    createdAt: '2026-09-01T12:00:00',
    ...overrides,
  }
}

function renderList(tasks: ApplicableTask[], onAnswer = vi.fn()) {
  get.mockResolvedValue([])
  render(wrap(<ApplicableTaskList tasks={tasks} onAnswer={onAnswer} renderFiles={() => null} />))
  return onAnswer
}

describe('ApplicableTaskList', () => {
  it('names every request the table is making', () => {
    renderList([task(), task({ taskId: 'task-2', title: 'Trasfondo' })])

    expect(screen.getByText('Ficha de personaje')).toBeInTheDocument()
    expect(screen.getByText('Trasfondo')).toBeInTheDocument()
  })

  /**
   * Somebody who has not applied still sees what will be asked of them — half of deciding whether to
   * apply (#206) — and the screen says why they cannot answer yet rather than just not offering a
   * button (principio 2 de frontend-diseno.md §1).
   */
  it('explains why a request cannot be answered instead of hiding the reason', async () => {
    const onAnswer = renderList([task({ canSubmit: false })])

    await userEvent.click(screen.getByRole('button', { name: /Ficha de personaje/ }))

    expect(screen.queryByRole('button', { name: 'Entregar' })).not.toBeInTheDocument()
    expect(screen.getByText('Vas a poder responder esto cuando te postules a la mesa.')).toBeInTheDocument()
    expect(onAnswer).not.toHaveBeenCalled()
  })

  it('offers to answer when the reader is in the audience', async () => {
    const onAnswer = renderList([task()])

    await userEvent.click(screen.getByRole('button', { name: /Ficha de personaje/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Entregar' }))

    expect(onAnswer).toHaveBeenCalledWith(expect.objectContaining({ taskId: 'task-1' }))
  })

  /**
   * Answers accumulate and none replaces another (#76), so a second one is offered as "again" and
   * what was already sent is counted rather than reduced to a tick.
   */
  it('says how many times the reader already answered, and offers to answer again', async () => {
    renderList([task({ mySubmissionCount: 2 })])

    expect(screen.getByText('Entregaste 2 veces')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Ficha de personaje/ }))
    expect(screen.getByRole('button', { name: 'Entregar de nuevo' })).toBeInTheDocument()
  })

  /** #70 in the interface: "important" is a label, and nothing on the screen threatens anybody. */
  it('shows mandatory as a label and never as a consequence', () => {
    renderList([task({ isMandatory: true })])

    expect(screen.getByText('Importante')).toBeInTheDocument()
    expect(screen.queryByText(/expuls/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/bloque/i)).not.toBeInTheDocument()
  })
})
