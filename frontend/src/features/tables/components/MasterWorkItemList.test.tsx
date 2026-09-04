import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { MasterWorkItemList } from './MasterWorkItemList'
import type { MasterWorkItem } from '../types'

function item(overrides: Partial<MasterWorkItem> & Pick<MasterWorkItem, 'kind'>): MasterWorkItem {
  return {
    tableId: 'table-1',
    tableName: 'La Cripta de Ondrak',
    subject: null,
    count: 1,
    since: '2026-09-01T12:00:00',
    ...overrides,
  }
}

function renderTray(items: MasterWorkItem[]) {
  render(
    <MemoryRouter>
      <MasterWorkItemList items={items} />
    </MemoryRouter>,
  )
}

describe('MasterWorkItemList', () => {
  /**
   * The whole point of #136: the row says what to do, not how many of something there are. The
   * backend sends a code and the numbers (#197) and the phrase is built here.
   */
  it('turns each kind of work into a sentence rather than a counter', () => {
    renderTray([
      item({ kind: 'CandidatesWaiting', count: 3 }),
      item({ kind: 'OverdueTaskMissing', count: 2, subject: 'Ficha de personaje' }),
      item({ kind: 'SessionToRecord', count: 1 }),
      item({ kind: 'ChangesRequested' }),
      item({ kind: 'ReadyToStart' }),
    ])

    expect(screen.getByText('3 personas esperando respuesta')).toBeInTheDocument()
    expect(screen.getByText('«Ficha de personaje» venció y 2 personas no entregaron')).toBeInTheDocument()
    expect(screen.getByText('1 sesión jugada sin registrar')).toBeInTheDocument()
    expect(screen.getByText('Un admin pidió correcciones')).toBeInTheDocument()
    expect(screen.getByText('La fecha de inicio ya pasó y la mesa no arrancó')).toBeInTheDocument()
  })

  /** Singular and plural are different sentences; a row reading "1 personas" reads as a bug. */
  it('says one person in the singular', () => {
    renderTray([item({ kind: 'CandidatesWaiting', count: 1 })])

    expect(screen.getByText('1 persona esperando respuesta')).toBeInTheDocument()
  })

  /**
   * Each kind is resolved on a different tab, and the row has to land on that one: a tray that only
   * names the problem leaves the reader to go find the screen, which is the work it saves them.
   */
  it('sends each kind to the screen where it is resolved', () => {
    renderTray([
      item({ kind: 'CandidatesWaiting' }),
      item({ kind: 'OverdueTaskMissing', subject: 'Ficha' }),
      item({ kind: 'SessionToRecord' }),
      item({ kind: 'ChangesRequested' }),
      item({ kind: 'ReadyToStart' }),
    ])

    expect(screen.getAllByRole('link', { name: 'Resolver' }).map((link) => link.getAttribute('href'))).toEqual([
      '/master/tables/table-1',
      '/master/tables/table-1/tasks',
      '/master/tables/table-1/sessions',
      '/master/tables/table-1/edit',
      '/master/tables/table-1/status',
    ])
  })

  /** The tray never re-sorts: the order is the server's, and it is the rule (#136). */
  it('renders the items in the order it was given them', () => {
    renderTray([
      item({ kind: 'SessionToRecord', tableId: 'table-2', tableName: 'Hijos del Vacio' }),
      item({ kind: 'CandidatesWaiting', tableId: 'table-1', tableName: 'La Cripta de Ondrak' }),
    ])

    expect(screen.getAllByRole('listitem').map((row) => row.textContent)).toEqual([
      expect.stringContaining('Hijos del Vacio'),
      expect.stringContaining('La Cripta de Ondrak'),
    ])
  })
})
