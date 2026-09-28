import { useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { adminPageSizeOptions } from '@/config/pagination'

/** What the pager needs. */
interface PaginationControlsProps {
  /** The current page, zero-based — the same base the backend uses. */
  page: number
  /** How many pages the listing has, as the backend counted them. */
  totalPages: number
  /** How many rows the listing has, across every page. */
  totalElements: number
  /** Called with the zero-based page the reader asked for — already inside `0..totalPages - 1`. */
  onPageChange: (page: number) => void
  /** Rows per page right now. Only read when `onPageSizeChange` is given. */
  pageSize?: number
  /**
   * Called with the size the reader picked. **Its presence is what shows the size selector** (#271):
   * an admin's working list passes it, a list whose size is a fixed decision (`/my/files`) does not.
   */
  onPageSizeChange?: (size: number) => void
}

/** One slot of the numbered strip: a page (zero-based) or a gap between two of them. */
type PageSlot = number | 'gap-start' | 'gap-end'

/**
 * The pages worth a button: the first, the last, and the current one with its neighbours. A gap of
 * exactly one page shows that page instead of an ellipsis — `1 … 3` hides less than it costs.
 */
function pageSlots(page: number, totalPages: number): PageSlot[] {
  const wanted = [0, page - 1, page, page + 1, totalPages - 1].filter((p) => p >= 0 && p < totalPages)
  const pages = [...new Set(wanted)].sort((a, b) => a - b)

  const slots: PageSlot[] = []
  pages.forEach((current, index) => {
    const previous = pages[index - 1]
    if (previous !== undefined && current - previous === 2) slots.push(previous + 1)
    else if (previous !== undefined && current - previous > 2) slots.push(slots.length <= 1 ? 'gap-start' : 'gap-end')
    slots.push(current)
  })
  return slots
}

/**
 * Pagination for a working list (#173, #271): where the reader is, how much there is, and **any page
 * one step away**.
 *
 * Three ways to move, because a working list is worked, not browsed: previous and next for the page
 * beside this one; a compact numbered strip — first, last, and the current one with its neighbours —
 * for the ends; and **"go to page"** for everything between, so page 900 of 1000 is a number and an
 * Enter, not 899 clicks. The listing's order is fixed and tie-broken by id on the server (#173), which
 * is what makes a page number mean the same rows twice.
 *
 * **The size selector is optional** (10 · 25 · 50 · 100, `adminPageSizeOptions`): it shows only when
 * the screen passes `onPageSizeChange`. With it, the pager stays visible on a single page as long as
 * there are more rows than the smallest size — otherwise picking 100 would hide the control that
 * brings you back to 10.
 *
 * Below `sm` the numbered strip gives way to "page X of Y" and the go-to box: the same reach in the
 * width of a phone.
 *
 * @param props.page             the current page, zero-based
 * @param props.totalPages       how many pages there are
 * @param props.totalElements    how many rows there are
 * @param props.onPageChange     called with the page asked for
 * @param props.pageSize         rows per page right now
 * @param props.onPageSizeChange called with the size picked; shows the selector
 */
export function PaginationControls({ page, totalPages, totalElements, onPageChange, pageSize, onPageSizeChange }: PaginationControlsProps) {
  const { t } = useTranslation('common')
  const [target, setTarget] = useState('')

  const canResize = onPageSizeChange !== undefined && totalElements > adminPageSizeOptions[0]
  if (totalPages <= 1 && !canResize) return null

  /** On Enter, jumps to the typed page, clamped into range, and clears the box. */
  function goTo(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter') return
    event.preventDefault()
    const typed = Number.parseInt(target, 10)
    setTarget('')
    if (Number.isNaN(typed)) return
    const next = Math.min(Math.max(typed, 1), totalPages) - 1
    if (next !== page) onPageChange(next)
  }

  return (
    <nav aria-label={t('pagination.label')} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3 pt-2">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <p className="text-fg-muted text-xs">
          {t('pagination.page', { page: page + 1, pages: Math.max(totalPages, 1) })} · {t('pagination.total', { total: totalElements })}
        </p>
        {canResize && (
          <label className="text-fg-muted flex items-center gap-2 text-xs">
            {t('pagination.pageSize')}
            <Select value={String(pageSize)} onValueChange={(value) => onPageSizeChange(Number(value))}>
              <SelectTrigger size="sm" aria-label={t('pagination.pageSize')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {adminPageSizeOptions.map((option) => (
                  <SelectItem key={option} value={String(option)}>
                    {option}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="icon-sm"
            onClick={() => onPageChange(page - 1)}
            disabled={page === 0}
            aria-label={t('pagination.previous')}
          >
            <ChevronLeft className="size-4" />
          </Button>
          <ol className="hidden items-center gap-1 sm:flex">
            {pageSlots(page, totalPages).map((slot) =>
              typeof slot === 'number' ? (
                <li key={slot}>
                  <Button
                    variant={slot === page ? 'default' : 'ghost'}
                    size="icon-sm"
                    aria-current={slot === page ? 'page' : undefined}
                    aria-label={t('pagination.pageNumber', { page: slot + 1 })}
                    onClick={() => slot !== page && onPageChange(slot)}
                  >
                    {slot + 1}
                  </Button>
                </li>
              ) : (
                <li key={slot} aria-hidden="true" className="text-fg-subtle px-1 text-sm">
                  …
                </li>
              ),
            )}
          </ol>
          <Button
            variant="outline"
            size="icon-sm"
            onClick={() => onPageChange(page + 1)}
            disabled={page + 1 >= totalPages}
            aria-label={t('pagination.next')}
          >
            <ChevronRight className="size-4" />
          </Button>
          <Input
            type="number"
            inputMode="numeric"
            min={1}
            max={totalPages}
            value={target}
            onChange={(event) => setTarget(event.target.value)}
            onKeyDown={goTo}
            placeholder={t('pagination.goTo')}
            aria-label={t('pagination.goToLabel', { pages: totalPages })}
            className="h-8 w-24 text-sm"
          />
        </div>
      )}
    </nav>
  )
}
