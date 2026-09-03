import type { ReactNode } from 'react'

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

/**
 * What a column is, and what happens to it once the screen is too narrow for a table.
 *
 * @template T the row type
 */
export interface DataTableColumn<T> {
  /** Stable key for this column. Also the React key of its cells. */
  id: string
  /** The column heading. Already translated by the caller - this component holds no strings. */
  header: string
  /** Renders the cell for one row. */
  cell: (row: T) => ReactNode
  /**
   * What this column becomes on a narrow screen:
   *
   * - `title` - the card's heading. Exactly one column should be the title.
   * - `badge` - sits next to the title, without a label. For state.
   * - `meta` (the default) - a labelled line in the card's body.
   * - `hidden` - dropped from the card. For anything that only earns its place in a wide table.
   */
  role?: 'title' | 'badge' | 'meta' | 'hidden'
  /** Extra classes for the header cell, typically alignment or width. */
  headerClassName?: string
}

/** What the table needs. */
export interface DataTableProps<T> {
  /** The column definitions, in the order they are shown. */
  columns: DataTableColumn<T>[]
  /** The rows to render. */
  rows: T[]
  /** Stable identity for a row - its React key. */
  getRowId: (row: T) => string
  /** Optional per-row actions. Rendered in a last column, and at the foot of each card. */
  renderActions?: (row: T) => ReactNode
  /** Accessible name for the table, since these screens rarely have a visible caption per table. */
  label: string
}

/**
 * A wide table that **stops being a table** when the screen is narrow.
 *
 * This is the expensive case of the responsive rules (frontend-diseno.md 5.b): /admin/catalogs,
 * /admin/users and /owner/audit all have five or more columns. Below `md` each row becomes a card -
 * identity and state at the top, the rest as labelled lines, actions at the foot - and **there is
 * never horizontal scroll**. A table you have to drag sideways on a phone is a table nobody reads.
 *
 * Both layouts are built from the same column definitions, which is the point: there is one
 * description of what a row contains, not a table and a card list that drift apart.
 *
 * It knows nothing about any domain - no entity crosses its props, only columns and rows (#3.1.2).
 * The four states (loading, empty, error, forbidden) belong to the screen around it, not here: this
 * component's job is rows it was handed.
 *
 * @template T the row type
 * @param props.columns       the column definitions
 * @param props.rows          the rows to render
 * @param props.getRowId      stable identity for a row
 * @param props.renderActions optional per-row actions
 * @param props.label         accessible name for the table
 */
export function DataTable<T>({ columns, rows, getRowId, renderActions, label }: DataTableProps<T>) {
  const titleColumn = columns.find((column) => column.role === 'title')
  const badgeColumns = columns.filter((column) => column.role === 'badge')
  const metaColumns = columns.filter((column) => column.role !== 'title' && column.role !== 'badge' && column.role !== 'hidden')

  return (
    <>
      {/* Wide: a real table, with real semantics. */}
      <div className="hidden md:block">
        <Table aria-label={label}>
          <TableHeader>
            <TableRow>
              {columns.map((column) => (
                <TableHead key={column.id} className={cn(column.headerClassName)}>
                  {column.header}
                </TableHead>
              ))}
              {renderActions && <TableHead className="w-px" />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={getRowId(row)}>
                {columns.map((column) => (
                  <TableCell key={column.id}>{column.cell(row)}</TableCell>
                ))}
                {renderActions && <TableCell className="text-right whitespace-nowrap">{renderActions(row)}</TableCell>}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* Narrow: one card per row. Same columns, different shape. */}
      <ul className="space-y-3 md:hidden">
        {rows.map((row) => (
          <li key={getRowId(row)} className="border-border space-y-3 rounded-lg border p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              {titleColumn && <span className="min-w-0 font-medium">{titleColumn.cell(row)}</span>}
              {badgeColumns.map((column) => (
                <span key={column.id}>{column.cell(row)}</span>
              ))}
            </div>
            <dl className="space-y-1 text-sm">
              {metaColumns.map((column) => (
                <div key={column.id} className="flex justify-between gap-4">
                  <dt className="text-fg-muted">{column.header}</dt>
                  <dd className="min-w-0 text-right">{column.cell(row)}</dd>
                </div>
              ))}
            </dl>
            {renderActions && <div className="flex flex-wrap justify-end gap-2 pt-1">{renderActions(row)}</div>}
          </li>
        ))}
      </ul>
    </>
  )
}
