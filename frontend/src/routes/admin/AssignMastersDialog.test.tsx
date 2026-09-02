import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

import '@/providers/i18n'
import type { UserSummary } from '@/features/users'
import { AssignMastersDialog } from './AssignMastersDialog'

const PEOPLE: UserSummary[] = [
  { id: 'user-1', discordUsername: 'juanma', name: 'Juan Manuel' },
  { id: 'user-2', discordUsername: 'pablosan', name: 'Pablo Ruiz' },
]

const mutate = vi.fn()

/** El buscador real tiene su propio test; acá interesa el orden de los chips y lo que se envía. */
vi.mock('@/features/users', () => ({
  UserPicker: ({ onSelect, excludedIds }: { onSelect: (user: UserSummary) => void; excludedIds: readonly string[] }) => (
    <div>
      {PEOPLE.filter((person) => !excludedIds.includes(person.id)).map((person) => (
        <button key={person.id} type="button" onClick={() => onSelect(person)}>
          {`Elegir ${person.discordUsername}`}
        </button>
      ))}
    </div>
  ),
}))

vi.mock('@/features/tables', () => ({
  useAssignMasters: () => ({ mutate, isPending: false }),
}))

function renderDialog() {
  return render(
    <MemoryRouter>
      <AssignMastersDialog tableId="table-1" tableName="Curse of Strahd" open onOpenChange={vi.fn()} />
    </MemoryRouter>,
  )
}

describe('AssignMastersDialog', () => {
  it('el primero que se agrega queda de Primary y el resto de Secondary', async () => {
    renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'Elegir juanma' }))
    await userEvent.click(screen.getByRole('button', { name: 'Elegir pablosan' }))
    await userEvent.click(screen.getByRole('button', { name: 'Asignar masters' }))

    expect(mutate).toHaveBeenCalledWith(
      { tableId: 'table-1', request: { primaryUserId: 'user-1', secondaryUserIds: ['user-2'] } },
      expect.anything(),
    )
  })

  it('tocar el chip de un Secondary lo asciende y degrada al Primary anterior', async () => {
    renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'Elegir juanma' }))
    await userEvent.click(screen.getByRole('button', { name: 'Elegir pablosan' }))
    await userEvent.click(screen.getByRole('button', { name: 'Hacer master a pablosan' }))
    await userEvent.click(screen.getByRole('button', { name: 'Asignar masters' }))

    expect(mutate).toHaveBeenLastCalledWith(
      { tableId: 'table-1', request: { primaryUserId: 'user-2', secondaryUserIds: ['user-1'] } },
      expect.anything(),
    )
  })

  it('el Primary no se ofrece para ascender: ya lo es', async () => {
    renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'Elegir juanma' }))

    expect(screen.queryByRole('button', { name: 'Hacer master a juanma' })).not.toBeInTheDocument()
  })

  it('quitar el Primary asciende al que seguía', async () => {
    renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'Elegir juanma' }))
    await userEvent.click(screen.getByRole('button', { name: 'Elegir pablosan' }))
    await userEvent.click(screen.getByRole('button', { name: 'Quitar a juanma' }))
    await userEvent.click(screen.getByRole('button', { name: 'Asignar masters' }))

    expect(mutate).toHaveBeenLastCalledWith(
      { tableId: 'table-1', request: { primaryUserId: 'user-2', secondaryUserIds: [] } },
      expect.anything(),
    )
  })

  it('no deja asignar sin nadie elegido', () => {
    renderDialog()

    expect(screen.getByRole('button', { name: 'Asignar masters' })).toBeDisabled()
  })
})
