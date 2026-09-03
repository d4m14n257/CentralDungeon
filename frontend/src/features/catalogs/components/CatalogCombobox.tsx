import { CheckIcon, ChevronsUpDownIcon, PlusIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useDebounce } from '@/hooks/useDebounce'
import { cn } from '@/lib/utils'

import { useCatalogValues } from '../api/useCatalogValues'
import { useProposeCatalogValue } from '../api/useProposeCatalogValue'
import type { CatalogKind, CatalogValue } from '../types'

/** What the combobox needs to know. */
export interface CatalogComboboxProps {
  /** Which catalog to pick from. */
  kind: CatalogKind
  /** The values already chosen. Used to mark them and to keep them out of the result list. */
  selected: CatalogValue[]
  /** Called with the value the person picked, or with the one they just proposed. */
  onSelect: (value: CatalogValue) => void
  /** Whether the person may propose a value that does not exist yet. Masters and admins may (#55). */
  canPropose?: boolean
}

/**
 * Picks a value from a catalog, and - when the person is allowed to - proposes one that is not
 * there yet.
 *
 * The list only ever holds accepted values, because that is all the backend returns (#57). What
 * makes this component more than a select is the second half: a master who does not find their
 * system can add it without leaving the wizard, and what comes back is marked as pending so they
 * are not left thinking everyone can already see it.
 *
 * The search is debounced rather than sent on every keystroke: a catalog is a small table, but this
 * runs inside a form somebody is typing into.
 *
 * @param props.kind        which catalog to pick from
 * @param props.selected    the values already chosen
 * @param props.onSelect    called with the picked or newly proposed value
 * @param props.canPropose  whether to offer proposing a missing value
 */
export function CatalogCombobox({ kind, selected, onSelect, canPropose = false }: CatalogComboboxProps) {
  const { t } = useTranslation('catalogs')
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search, 250)

  const { data, isPending } = useCatalogValues(kind, debouncedSearch)
  const propose = useProposeCatalogValue(kind)

  const selectedIds = new Set(selected.map((value) => value.id))
  const options = data?.content ?? []

  const trimmed = search.trim()
  // Only offer to propose something that is not already an option: an exact match, however it is
  // cased, is the value that already exists - the server would answer 409 anyway.
  const alreadyExists = options.some((option) => option.name.toLowerCase() === trimmed.toLowerCase())
  const showPropose = canPropose && trimmed.length > 0 && !alreadyExists && !isPending

  function handleSelect(value: CatalogValue) {
    onSelect(value)
    setSearch('')
    setOpen(false)
  }

  function handlePropose() {
    propose.mutate(trimmed, { onSuccess: handleSelect })
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" role="combobox" aria-expanded={open} className="w-full justify-between">
          {t(`kind.${kind}.placeholder`)}
          <ChevronsUpDownIcon className="ml-2 size-4 shrink-0 opacity-50" aria-hidden="true" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-(--radix-popover-trigger-width) p-0" align="start">
        {/* The filtering is the server's, so cmdk must not filter the list a second time. */}
        <Command shouldFilter={false}>
          <CommandInput placeholder={t(`kind.${kind}.searchPlaceholder`)} value={search} onValueChange={setSearch} />
          <CommandList>
            {isPending && <CommandEmpty>{t('combobox.loading')}</CommandEmpty>}
            {!isPending && options.length === 0 && !showPropose && <CommandEmpty>{t('combobox.noResults')}</CommandEmpty>}
            {options.length > 0 && (
              <CommandGroup>
                {options.map((option) => (
                  <CommandItem key={option.id} value={option.id} onSelect={() => handleSelect(option)}>
                    <CheckIcon className={cn('mr-2 size-4', selectedIds.has(option.id) ? 'opacity-100' : 'opacity-0')} aria-hidden="true" />
                    {option.name}
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
            {showPropose && (
              <CommandGroup>
                <CommandItem value={`propose-${trimmed}`} onSelect={handlePropose} disabled={propose.isPending}>
                  <PlusIcon className="mr-2 size-4" aria-hidden="true" />
                  {t('combobox.propose', { name: trimmed })}
                </CommandItem>
                {/* #57 in one line: what the master is about to create is not visible to anyone else yet. */}
                <p className="text-fg-subtle px-3 pb-2 text-xs">{t('combobox.proposeHint')}</p>
              </CommandGroup>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
