import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { DataTable, type DataTableColumn } from './DataTable'

interface Row {
  id: string
  name: string
  status: string
  group: string
  uses: number
}

const rows: Row[] = [
  { id: 'r-1', name: 'D&D 5e', status: 'Aceptado', group: 'Es el principal', uses: 4 },
  { id: 'r-2', name: 'DANDD', status: 'Aceptado', group: 'D&D 5e', uses: 1 },
]

const columns: DataTableColumn<Row>[] = [
  { id: 'name', header: 'Nombre', role: 'title', cell: (row) => row.name },
  { id: 'status', header: 'Estado', role: 'badge', cell: (row) => row.status },
  { id: 'group', header: 'Grupo', cell: (row) => row.group },
  { id: 'uses', header: 'Mesas', cell: (row) => row.uses },
]

/**
 * Renders the table with the fixture above.
 *
 * @param extraColumns columns appended for one case
 * @returns the render result
 */
function renderTable(extraColumns: DataTableColumn<Row>[] = []) {
  return render(
    <DataTable
      label="Catálogo de prueba"
      columns={[...columns, ...extraColumns]}
      rows={rows}
      getRowId={(row) => row.id}
      renderActions={(row) => <button type="button">Editar {row.name}</button>}
    />,
  )
}

describe('DataTable', () => {
  it('renders a real table with real semantics on a wide screen', () => {
    renderTable()

    const table = screen.getByRole('table', { name: 'Catálogo de prueba' })
    expect(within(table).getByRole('columnheader', { name: 'Nombre' })).toBeInTheDocument()
    expect(within(table).getAllByRole('row')).toHaveLength(rows.length + 1)
  })

  /**
   * The expensive responsive case (frontend-diseno.md 5.b): both layouts exist in the DOM and CSS
   * decides which one is shown, so the same data is reachable either way - and neither is a table
   * that has to be dragged sideways.
   */
  it('also renders every row as a card, from the same column definitions', () => {
    renderTable()

    const cards = screen.getAllByRole('listitem')
    expect(cards).toHaveLength(rows.length)
    expect(within(cards[0] as HTMLElement).getByText('D&D 5e')).toBeInTheDocument()
    // A meta column keeps its header as the label of its value - a bare number would say nothing.
    expect(within(cards[0] as HTMLElement).getByText('Mesas')).toBeInTheDocument()
  })

  /**
   * The rule is about the narrow screen: a table nobody can read without dragging it sideways
   * (frontend-diseno.md 5.b). The card list is what a phone gets, and it must not scroll - the
   * `overflow-x-auto` shadcn puts around the table itself is on the layout a phone never sees.
   */
  it('never puts the card list in a horizontally scrolling container', () => {
    renderTable()

    const cardList = screen.getAllByRole('listitem')[0]?.parentElement
    expect(cardList?.className).not.toMatch(/overflow-x/)
    expect(cardList?.closest('.overflow-x-auto')).toBeNull()
  })

  it('drops a hidden column from the card but keeps it in the table', () => {
    renderTable([{ id: 'created', header: 'Creado', role: 'hidden', cell: () => '2026-01-01' }])

    expect(within(screen.getByRole('table')).getByRole('columnheader', { name: 'Creado' })).toBeInTheDocument()
    const firstCard = screen.getAllByRole('listitem')[0] as HTMLElement
    expect(within(firstCard).queryByText('Creado')).not.toBeInTheDocument()
  })

  it('renders the row actions in both layouts', () => {
    renderTable()

    // Once in the table row, once in the card: two nodes for the same action, one visible at a time.
    expect(screen.getAllByRole('button', { name: 'Editar D&D 5e' })).toHaveLength(2)
  })
})
