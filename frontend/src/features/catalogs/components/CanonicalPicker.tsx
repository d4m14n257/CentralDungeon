import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { useDebounce } from '@/hooks/useDebounce'
import { cn } from '@/lib/utils'

import { useAdminCatalog } from '../api/useAdminCatalog'
import type { AdminCatalogValue, CatalogKind } from '../types'

/** What the picker needs. */
export interface CanonicalPickerProps {
  /** Which catalog to pick a group from. */
  kind: CatalogKind
  /** The value currently picked, or null for none. */
  selectedId: string | null
  /** Called with the picked group, or with null when the person clears the choice. */
  onSelect: (value: AdminCatalogValue | null) => void
  /** A value to leave out - typically the one the dialog is about, so it cannot point at itself. */
  excludeId?: string
  /** Label for the "no group" option, or omit to make a choice mandatory. */
  noneLabel?: string
}

/**
 * Picks a **canonical entry** - a synonym group - out of a catalog.
 *
 * It exists because three of the six admin operations take a group rather than a value, and a group
 * is exactly a canonical entry (#59). Offering aliases here would let an admin build the second
 * level the flat model forbids: the server would answer 409, but a control that offers a choice it
 * knows will be refused is the grey button that does not say why it is grey
 * (frontend-diseno.md 1, principio 2).
 *
 * So the list is filtered twice on purpose - accepted by the server, canonical by us - and what is
 * left is exactly what the operation accepts.
 *
 * @param props.kind       which catalog
 * @param props.selectedId the group currently picked
 * @param props.onSelect   called with the picked group, or null
 * @param props.excludeId  a value to leave out, so nothing can point at itself
 * @param props.noneLabel  label for the "no group" option; omit to require a choice
 */
export function CanonicalPicker({ kind, selectedId, onSelect, excludeId, noneLabel }: CanonicalPickerProps) {
  const { t } = useTranslation('catalogs')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search, 250)

  const { data, isPending } = useAdminCatalog(kind, debouncedSearch, ['Accepted'], 0)

  const groups = (data?.content ?? []).filter((value) => value.canonicalId === null && value.id !== excludeId)

  return (
    <Command shouldFilter={false} className="border-border rounded-md border">
      <CommandInput placeholder={t('picker.searchPlaceholder')} value={search} onValueChange={setSearch} />
      <CommandList>
        {isPending && <CommandEmpty>{t('combobox.loading')}</CommandEmpty>}
        {!isPending && groups.length === 0 && <CommandEmpty>{t('picker.noGroups')}</CommandEmpty>}
        <CommandGroup>
          {noneLabel && (
            <CommandItem value="__none__" onSelect={() => onSelect(null)} className={cn(selectedId === null && 'bg-accent')}>
              {noneLabel}
            </CommandItem>
          )}
          {groups.map((group) => (
            <CommandItem
              key={group.id}
              value={group.id}
              onSelect={() => onSelect(group)}
              className={cn(selectedId === group.id && 'bg-accent')}
            >
              {group.name}
            </CommandItem>
          ))}
        </CommandGroup>
      </CommandList>
    </Command>
  )
}
