import { cn } from '@/lib/utils'
import type { CatalogValue } from '@/types/catalog'

import { CatalogChip } from './CatalogChip'
import { CatalogCombobox } from './CatalogCombobox'
import type { CatalogKind } from '../types'

interface CatalogPickerProps {
  /** Which catalog is being picked from: systems, tags or platforms. */
  kind: CatalogKind
  /** The block's label, already passed through `t()`. */
  label: string
  /** What is chosen so far. Each value keeps the alias its author gave it (#58). */
  selected: CatalogValue[]
  /** Called with the new set. It is a set, not a diff: the whole selection is replaced (#190). */
  onChange: (values: CatalogValue[]) => void
  /**
   * What is missing from this block, already translated, or null when it is fine (#228).
   *
   * Given it, the block marks *itself* — label, border and message. A single sentence at the foot of
   * a form with three of these says something is wrong without saying which one, and the reader has
   * to work that out by elimination.
   */
  error?: string | null
}

/**
 * One catalog block of a table form: the chips already chosen plus the combobox that adds another.
 *
 * A master who does not find their system proposes it here and keeps going; what comes back is
 * marked as pending, because the table publishes while the value waits for an admin (#55, #57).
 *
 * It lives in `features/catalogs` because it composes only that feature's own two components — and
 * because both the wizard and the edit form need it, which is what makes it a component rather than
 * a piece of one screen.
 *
 * @param props.kind     which catalog to offer
 * @param props.label    the block's label
 * @param props.selected what is chosen so far
 * @param props.onChange called with the new set
 * @param props.error    what this block is missing, or null when it is fine
 */
export function CatalogPicker({ kind, label, selected, onChange, error }: CatalogPickerProps) {
  return (
    <div className="space-y-2">
      <p className={cn('text-sm font-medium', error && 'text-state-canceled-fg')}>{label}</p>
      {selected.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {selected.map((value) => (
            <CatalogChip key={value.id} value={value} onRemove={(id) => onChange(selected.filter((item) => item.id !== id))} />
          ))}
        </div>
      )}
      <div className={cn(error && 'ring-state-canceled-dot rounded-md ring-1')}>
        <CatalogCombobox
          kind={kind}
          selected={selected}
          canPropose
          onSelect={(value) => {
            if (!selected.some((item) => item.id === value.id)) {
              onChange([...selected, value])
            }
          }}
        />
      </div>
      {error && (
        <p role="alert" className="text-state-canceled-fg text-sm">
          {error}
        </p>
      )}
    </div>
  )
}
